package com.keeply.agent.daemon;

import com.keeply.agent.api.BackendClient;
import com.keeply.agent.api.LogUtils;
import com.keeply.agent.auth.DeviceAuthStore;
import com.keeply.agent.config.AgentConfig;
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
    private final LocalDatabase db;
    private final SourceBackupExecutor sourceBackupExecutor;
    private final DeviceAuthStore authStore;
    private UUID deviceId;

    public BackupCycleRunner(AgentConfig config, LocalDatabase db, DeviceAuthStore authStore) {
        this.backend = new BackendClient(config.backend().url(), authStore);
        this.db = db;
        this.authStore = authStore;
        this.sourceBackupExecutor = (id, source) -> new BackupEngine(backend, db).backup(id, source);
    }

    BackupCycleRunner(AgentConfig config, DeviceAuthStore authStore, SourceBackupExecutor sourceBackupExecutor) {
        this.backend = new BackendClient(config.backend().url(), authStore);
        this.db = null;
        this.authStore = authStore;
        this.sourceBackupExecutor = sourceBackupExecutor;
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
        Optional<ProtectionPlan> maybePlan = backend.getDevicePlan(currentDeviceId);
        if (maybePlan.isEmpty()) {
            log.warn("event=backup.cycle status=skipped reason=missing_protection_plan");
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
}
