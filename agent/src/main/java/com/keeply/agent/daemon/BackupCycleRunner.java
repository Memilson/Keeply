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
        this.backend = new BackendClient(config.backend().url());
        this.db = db;
        this.authStore = authStore;
        this.sourceBackupExecutor = (id, source) -> new BackupEngine(backend, db).backup(id, source);
    }

    BackupCycleRunner(AgentConfig config, DeviceAuthStore authStore, SourceBackupExecutor sourceBackupExecutor) {
        this.config = config;
        this.backend = new BackendClient(config.backend().url());
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
            LogUtils.logError(log, "event=backup.cycle status=failed stage=auth_or_registration", e);
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
                    log.warn("event=backup.source status=retrying source={} reason=invalid_device", sourceName);
                    try {
                        deviceId = null;
                        authenticate(true);
                        ensureDeviceRegistered();
                        UUID retriedSnapshotId = sourceBackupExecutor.run(deviceId, source);
                        log.info("event=backup.source status=completed_after_reregister source={} snapshot_id={}", sourceName, retriedSnapshotId);
                        continue;
                    } catch (Exception retryError) {
                        log.error("event=backup.source status=failed_after_reregister source={} message={}", sourceName, retryError.getMessage());
                    }
                } else {
                    log.error("event=backup.source status=failed source={} message={}", sourceName, e.getMessage());
                }
            }
        }
    }

    private boolean isInvalidDeviceError(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            String message = current.getMessage();
            if (message != null && (message.contains("Device inválido") || message.contains("Falha ao iniciar snapshot"))) {
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
                authStore.save(refreshed);
                deviceId = refreshed.deviceId();
                return;
            } catch (Exception e) {
                log.warn("event=auth.session status=refresh_failed action=login_required");
            }
        }
        try {
            String hostname = InetAddress.getLocalHost().getHostName();
            String installationId = saved != null && saved.deviceInstallationId() != null && !saved.deviceInstallationId().isBlank()
                    ? saved.deviceInstallationId()
                    : DeviceIdentity.getOrCreate();
            DeviceSession session = backend.loginDevice(
                    config.auth().email(),
                    config.auth().password(),
                    installationId,
                    hostname,
                    System.getProperty("os.name"),
                    "0.1.0-daemon"
            );
            authStore.save(session);
            deviceId = session.deviceId();
        } catch (Exception e) {
            throw new IllegalStateException("Falha de autenticação do daemon. Abra a UI e faça login novamente.", e);
        }
    }

    private void ensureDeviceRegistered() throws Exception {
        if (deviceId != null) {
            return;
        }
        throw new IllegalStateException("Device não autenticado no ciclo atual.");
    }
}
