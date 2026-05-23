package com.keeply.agent.daemon;

import com.keeply.agent.api.BackendClient;
import com.keeply.agent.auth.DeviceAuthStore;
import com.keeply.agent.auth.DeviceIdentity;
import com.keeply.agent.config.AgentConfig;
import com.keeply.agent.model.ProtectionPlan;
import com.keeply.agent.core.BackupEngine;
import com.keeply.agent.core.LocalDatabase;
import com.keeply.agent.model.DeviceSession;

import java.net.InetAddress;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

public final class BackupCycleRunner {
    @FunctionalInterface
    interface SourceBackupExecutor {
        UUID run(UUID deviceId, Path source) throws Exception;
    }

    private final AgentConfig config;
    private final DaemonLogger logger;
    private final AtomicBoolean running = new AtomicBoolean(false);

    private final BackendClient backend;
    private final LocalDatabase db;
    private final SourceBackupExecutor sourceBackupExecutor;
    private final DeviceAuthStore authStore;
    private UUID deviceId;

    public BackupCycleRunner(AgentConfig config, LocalDatabase db, DeviceAuthStore authStore, DaemonLogger logger) {
        this.config = config;
        this.logger = logger;
        this.backend = new BackendClient(config.backend().url());
        this.db = db;
        this.authStore = authStore;
        this.sourceBackupExecutor = (id, source) -> new BackupEngine(backend, db).backup(id, source, logger::info);
    }

    BackupCycleRunner(AgentConfig config, DeviceAuthStore authStore, DaemonLogger logger, SourceBackupExecutor sourceBackupExecutor) {
        this.config = config;
        this.logger = logger;
        this.backend = new BackendClient(config.backend().url());
        this.db = null;
        this.authStore = authStore;
        this.sourceBackupExecutor = sourceBackupExecutor;
    }

    public void runCycle() {
        if (!running.compareAndSet(false, true)) {
            logger.warn("Execução ignorada: backup anterior ainda em andamento.");
            return;
        }

        try {
            authenticate(false);
            ensureDeviceRegistered();
            backupAllSources(deviceId);
        } catch (Exception e) {
            logger.error("Ciclo abortado por falha de autenticação/registro", e);
        } finally {
            running.set(false);
        }
    }

    void backupAllSources(UUID currentDeviceId) {
        Optional<ProtectionPlan> maybePlan = backend.getDevicePlan(currentDeviceId);
        if (maybePlan.isEmpty()) {
            logger.warn("Plano de proteção ausente para o device; ciclo cancelado.");
            return;
        }
        logger.info("Executando ciclo com deviceId=" + currentDeviceId);
        List<Path> sources = maybePlan.get().sources().stream().map(Path::of).toList();
        backupAllSources(currentDeviceId, sources);
    }

    void backupAllSources(UUID currentDeviceId, List<Path> sources) {
        for (Path source : sources) {
            String sourceName = source.getFileName().toString();
            try {
                logger.info("Iniciando backup de " + sourceName);
                UUID snapshotId = sourceBackupExecutor.run(currentDeviceId, source);
                logger.info("Backup concluído para " + sourceName + " snapshot=" + snapshotId);
            } catch (Exception e) {
                if (isInvalidDeviceError(e)) {
                    logger.warn("Device inválido detectado; tentando re-registrar e repetir origem " + sourceName);
                    try {
                        deviceId = null;
                        authenticate(true);
                        ensureDeviceRegistered();
                        UUID retriedSnapshotId = sourceBackupExecutor.run(deviceId, source);
                        logger.info("Backup concluído após re-registro para " + sourceName + " snapshot=" + retriedSnapshotId);
                        continue;
                    } catch (Exception retryError) {
                        logger.error("Falha no retry após re-registro da origem " + sourceName, retryError);
                    }
                } else {
                    logger.error("Falha no backup da origem " + sourceName, e);
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
                logger.warn("Sessão local inválida/revogada; novo login necessário.");
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
