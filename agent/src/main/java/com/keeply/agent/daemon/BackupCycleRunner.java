package com.keeply.agent.daemon;

import com.keeply.agent.api.BackendClient;
import com.keeply.agent.api.LogUtils;
import com.keeply.agent.auth.DeviceAuthStore;
import com.keeply.agent.auth.DeviceIdentity;
import com.keeply.agent.config.AgentConfig;
import com.keeply.agent.model.ProtectionPlan;
import com.keeply.agent.core.BackupEngine;
import com.keeply.agent.core.LocalDatabase;
import com.keeply.agent.model.DeviceSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
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

    private final AgentConfig config;
    private final AtomicBoolean running = new AtomicBoolean(false);

    private final BackendClient backend;
    private final LocalDatabase db;
    private final SourceBackupExecutor sourceBackupExecutor;
    private final DeviceAuthStore authStore;
    private UUID deviceId;

    public BackupCycleRunner(AgentConfig config, LocalDatabase db, DeviceAuthStore authStore) {
        this.config = config;
        this.backend = new BackendClient(config.backend().url(), authStore);
        this.db = db;
        this.authStore = authStore;
        this.sourceBackupExecutor = (id, source) -> new BackupEngine(backend, db).backup(id, source);
    }

    BackupCycleRunner(AgentConfig config, DeviceAuthStore authStore, SourceBackupExecutor sourceBackupExecutor) {
        this.config = config;
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
                if (isInvalidDeviceError(e)) {
                    log.warn("event=backup.source status=retrying source={} reason=invalid_device message={}", sourceName, e.getMessage());
                    try {
                        deviceId = null;
                        authenticate(true);
                        ensureDeviceRegistered();
                        UUID retriedSnapshotId = sourceBackupExecutor.run(deviceId, source);
                        log.info("event=backup.source status=completed_after_reregister source={} snapshot_id={}", sourceName, retriedSnapshotId);
                        continue;
                    } catch (Exception retryError) {
                        LogUtils.logError(log, "event=backup.source status=failed_after_reregister source=" + sourceName, retryError);
                    }
                } else if (isNetworkError(e)) {
                    log.warn("event=backup.source status=skipped source={} reason=network_error message={}", sourceName, e.getMessage());
                } else {
                    LogUtils.logError(log, "event=backup.source status=failed source=" + sourceName, e);
                }
            }
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

        String email = config.auth().email();
        String password = config.auth().password();

        if (password == null || password.isBlank()) {
            throw new IllegalStateException("Senha não configurada. O daemon não pode renovar a sessão automaticamente sem uma senha salva.");
        }

        try {
            String hostname = InetAddress.getLocalHost().getHostName();
            String installationId = saved != null && saved.deviceInstallationId() != null && !saved.deviceInstallationId().isBlank()
                    ? saved.deviceInstallationId()
                    : DeviceIdentity.getOrCreate();
            DeviceSession session = backend.loginDevice(
                    email,
                    password,
                    installationId,
                    hostname,
                    System.getProperty("os.name"),
                    "0.1.0-daemon"
            );
            deviceId = session.deviceId();
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
