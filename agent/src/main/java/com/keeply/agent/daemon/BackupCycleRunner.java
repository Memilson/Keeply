package com.keeply.agent.daemon;

import com.keeply.agent.api.BackendClient;
import com.keeply.agent.api.LogUtils;
import com.keeply.agent.auth.DeviceAuthStore;
import com.keeply.agent.config.AgentConfig;
import com.keeply.agent.config.AgentConfigReader;
import com.keeply.agent.config.AgentConfigWriter;
import com.keeply.agent.config.ProtectionPlanSyncService;
import com.keeply.agent.model.ProtectionPlan;
import com.keeply.agent.core.BackupEngine;
import com.keeply.agent.core.BackupSnapshotException;
import com.keeply.agent.core.LocalDatabase;
import com.keeply.agent.model.DeviceSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

public final class BackupCycleRunner {
    private static final Logger log = LoggerFactory.getLogger(BackupCycleRunner.class);

    @FunctionalInterface
    interface SourceBackupExecutor {
        UUID run(UUID deviceId, Path source) throws Exception;
    }

    private final AtomicBoolean running = new AtomicBoolean(false);

    private final BackendClient backend;
    private final AgentConfig config;
    private final AgentConfigWriter configWriter;
    private final AgentConfigReader configReader;
    private final LocalDatabase db;
    private final SourceBackupExecutor sourceBackupExecutor;
    private final DeviceAuthStore authStore;
    private UUID deviceId;

    public BackupCycleRunner(AgentConfig config, Path configPath, LocalDatabase db, DeviceAuthStore authStore) {
        this(config, new BackendClient(config.backend().url(), authStore), new AgentConfigWriter(configPath),
                new AgentConfigReader(configPath), db, authStore, null);
    }

    BackupCycleRunner(AgentConfig config, DeviceAuthStore authStore, SourceBackupExecutor sourceBackupExecutor) {
        this(config, new BackendClient(config.backend().url(), authStore), null, null, null, authStore, sourceBackupExecutor);
    }

    BackupCycleRunner(AgentConfig config, BackendClient backend, AgentConfigWriter configWriter,
                      LocalDatabase db, DeviceAuthStore authStore, SourceBackupExecutor sourceBackupExecutor) {
        this(config, backend, configWriter, null, db, authStore, sourceBackupExecutor);
    }

    BackupCycleRunner(AgentConfig config, BackendClient backend, AgentConfigWriter configWriter,
                      AgentConfigReader configReader, LocalDatabase db, DeviceAuthStore authStore,
                      SourceBackupExecutor sourceBackupExecutor) {
        this.config = config;
        this.backend = backend;
        this.configWriter = configWriter;
        this.configReader = configReader;
        this.db = db;
        this.authStore = authStore;
        this.sourceBackupExecutor = sourceBackupExecutor != null
                ? sourceBackupExecutor
                : (id, source) -> new BackupEngine(backend, db).backup(id, source);
    }

    public void runCycle() {
        if (!running.compareAndSet(false, true)) {
            log.warn("event=backup.cycle status=skipped reason=already_running");
            return;
        }

        try {
            log.info("event=backup.cycle status=started");
            authenticate(false);
            ensureDeviceRegistered();
            backupAllSources(deviceId);
        } catch (Exception e) {
            if (isNetworkError(e)) {
                log.warn("event=backup.cycle status=skipped reason=network_error message={}", e.getMessage());
            } else {
                LogUtils.logError(log, "event=backup.cycle status=failed stage=auth_or_registration", e);
            }
        } finally {
            log.info("event=backup.cycle status=finished");
            running.set(false);
        }
    }

    void backupAllSources(UUID currentDeviceId) {
        Optional<ProtectionPlan> maybePlan = resolvePlan(currentDeviceId);
        if (maybePlan.isEmpty()) {
            log.warn("event=backup.cycle status=skipped reason=missing_remote_and_local_plan");
            return;
        }
        log.info("event=backup.cycle device_id={} status=running_sources", currentDeviceId);
        List<Path> sources = maybePlan.get().sources().stream().map(Path::of).toList();
        backupAllSources(currentDeviceId, sources);
    }

    void backupAllSources(UUID currentDeviceId, List<Path> sources) {
        for (Path source : sources) {
            String sourceName = source.getFileName().toString();
            try {
                log.info("event=backup.source status=started source={}", sourceName);
                UUID snapshotId = sourceBackupExecutor.run(currentDeviceId, source);
                log.info("event=backup.source status=completed source={} snapshot_id={}", sourceName, snapshotId);
            } catch (Exception e) {
                if (e instanceof BackupSnapshotException backupError) {
                    markSnapshotFailed(currentDeviceId, backupError);
                } else if (isInvalidDeviceError(e)) {
                    log.warn("event=backup.source status=retrying source={} reason=invalid_device message={}", sourceName, e.getMessage());
                    try {
                        deviceId = null;
                        authenticate(true);
                        ensureDeviceRegistered();
                        UUID retriedSnapshotId = sourceBackupExecutor.run(deviceId, source);
                        log.info("event=backup.source status=completed_after_reregister source={} snapshot_id={}", sourceName, retriedSnapshotId);
                        continue;
                    } catch (Exception retryError) {
                        if (retryError instanceof BackupSnapshotException backupError) {
                            markSnapshotFailed(deviceId, backupError);
                        } else {
                            LogUtils.logError(log, "event=backup.source status=failed_after_reregister source=" + sourceName, retryError);
                        }
                    }
                } else if (isNetworkError(e)) {
                    log.warn("event=backup.source status=skipped source={} reason=network_error message={}", sourceName, e.getMessage());
                } else {
                    LogUtils.logError(log, "event=backup.source status=failed source=" + sourceName, e);
                }
            }
        }
    }

    private void markSnapshotFailed(UUID currentDeviceId, BackupSnapshotException error) {
        Exception failSnapshotError = null;
        try {
            backend.failSnapshot(error.snapshotId(), error.userMessage());
        } catch (Exception e) {
            failSnapshotError = e;
            log.warn("event=backup.snapshot status=fail_report_failed snapshot_id={} message={}",
                    error.snapshotId(), e.getMessage());
        }
        if (db != null) {
            db.setLastFailedSnapshot(currentDeviceId, error.sourcePath(), error.snapshotId().toString(), error.userMessage());
        }
        log.error("event=backup.source status=failed snapshot_id={} source_path={} message={}",
                error.snapshotId(), error.sourcePath(), error.userMessage(), error);
        if (failSnapshotError != null) {
            throw new IllegalStateException("Falha ao marcar snapshot como falho no backend", failSnapshotError);
        }
    }

    private boolean isInvalidDeviceError(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            String message = current.getMessage();
            if (message != null && message.contains("Device inválido")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private boolean isNetworkError(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof java.io.IOException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private void authenticate(boolean forceLoginDevice) {
        DeviceSession saved = authStore.load().orElse(null);
        if (!forceLoginDevice && saved != null) {
            backend.setSession(saved);
            try {
                DeviceSession refreshed = backend.refreshSession();
                deviceId = refreshed.deviceId();
                return;
            } catch (Exception e) {
                if (isNetworkError(e)) {
                    throw new IllegalStateException("Erro de rede ao tentar renovar sessão", e);
                }
                log.warn("event=auth.session status=refresh_failed action=login_required reason={}", e.getMessage());
            }
        }

        // Sessão expirou e não conseguimos renovar — o usuário precisa abrir o agente e fazer login novamente.
        try {
            throw new IllegalStateException(
                "Sessão expirada e não foi possível renovar automaticamente. " +
                "Abra o agente Keeply e faça login para reautorizar o daemon.");
        } catch (Exception e) {
            if (isNetworkError(e)) {
                throw new IllegalStateException("Erro de rede ao tentar realizar login", e);
            }
            throw new IllegalStateException("Falha de autenticação do daemon. Verifique se o login está correto na UI.", e);
        }
    }

    private void ensureDeviceRegistered() throws Exception {
        if (deviceId != null) {
            return;
        }
        throw new IllegalStateException("Device não autenticado no ciclo atual.");
    }

    private Optional<ProtectionPlan> resolvePlan(UUID currentDeviceId) {
        ProtectionPlanSyncService syncService = syncService();
        if (syncService == null) {
            Optional<ProtectionPlan> localPlan = localCachedPlan();
            localPlan.ifPresentOrElse(
                    ignored -> log.info("event=backup.plan status=loaded source=agent_yaml"),
                    () -> log.warn("event=backup.plan status=missing source=agent_yaml"));
            return localPlan;
        }
        try {
            ProtectionPlanSyncService.ReconciledPlan reconciled = syncService.reconcile(
                    currentDeviceId,
                    config.backend().url(),
                    config.auth() != null ? config.auth().email() : null);
            if (reconciled.plan() != null) {
                log.info("event=backup.plan status=loaded source={} sync_pending={}",
                        reconciled.source().name().toLowerCase(), reconciled.syncPending());
                return Optional.of(reconciled.plan());
            }
            log.warn("event=backup.plan status=missing source=none");
            return Optional.empty();
        } catch (RuntimeException e) {
            if (isNetworkError(e)) {
                log.warn("event=backup.plan status=fallback_to_local reason=network_error message={}", e.getMessage());
                return localCachedPlan();
            }
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao reconciliar plano do backup", e);
        }
    }

    private Optional<ProtectionPlan> localCachedPlan() {
        List<Path> configuredSources = config.backup() != null && config.backup().sources() != null
                ? config.backup().sources()
                : List.of();
        if (configuredSources.isEmpty()) {
            return Optional.empty();
        }
        String cron = config.schedule() != null ? config.schedule().cron() : null;
        ProtectionPlan.RetentionMode retentionMode = ProtectionPlan.RetentionMode.KEEP_ALL;
        Integer retentionDays = null;
        if (config.retention() != null && config.retention().mode() != null) {
            retentionMode = ProtectionPlan.RetentionMode.valueOf(config.retention().mode());
            retentionDays = config.retention().days();
        }
        ProtectionPlan.PlanType planType = configuredSources.size() == 1
                ? ProtectionPlan.PlanType.DEFAULT
                : ProtectionPlan.PlanType.CUSTOM;
        return Optional.of(new ProtectionPlan(
                planType,
                configuredSources.stream().map(Path::toString).toList(),
                config.cdp() != null && Boolean.TRUE.equals(config.cdp().enabled()),
                config.validation() != null && Boolean.TRUE.equals(config.validation().enabled()),
                config.encryption() != null && Boolean.TRUE.equals(config.encryption().enabled()),
                cron,
                retentionMode,
                retentionDays,
                config.planSync() != null ? config.planSync().lastRemoteUpdatedAt() : null));
    }

    private ProtectionPlanSyncService syncService() {
        if (configWriter == null || configReader == null) {
            return null;
        }
        return new ProtectionPlanSyncService(backend, configReader, configWriter);
    }
}
