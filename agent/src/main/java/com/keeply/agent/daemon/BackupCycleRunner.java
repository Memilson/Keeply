package com.keeply.agent.daemon;

import com.keeply.agent.api.BackendClient;
import com.keeply.agent.config.AgentConfig;
import com.keeply.agent.model.ProtectionPlan;
import com.keeply.agent.core.BackupEngine;
import com.keeply.agent.core.LocalDatabase;

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
    private UUID deviceId;

    public BackupCycleRunner(AgentConfig config, Path databasePath, DaemonLogger logger) {
        this.config = config;
        this.logger = logger;
        this.backend = new BackendClient(config.backend().url());
        this.db = new LocalDatabase(databasePath.toAbsolutePath().toString());
        this.sourceBackupExecutor = (id, source) -> new BackupEngine(backend, db).backup(id, source, logger::info);
    }

    BackupCycleRunner(AgentConfig config, DaemonLogger logger, SourceBackupExecutor sourceBackupExecutor) {
        this.config = config;
        this.logger = logger;
        this.backend = new BackendClient(config.backend().url());
        this.db = null;
        this.sourceBackupExecutor = sourceBackupExecutor;
    }

    public void runCycle() {
        if (!running.compareAndSet(false, true)) {
            logger.warn("Execução ignorada: backup anterior ainda em andamento.");
            return;
        }

        try {
            authenticate();
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
        List<Path> sources = maybePlan.get().sources().stream().map(Path::of).toList();
        backupAllSources(currentDeviceId, sources);
    }

    void backupAllSources(UUID currentDeviceId, List<Path> sources) {
        for (Path source : sources) {
            try {
                logger.info("Iniciando backup de " + source.toAbsolutePath());
                UUID snapshotId = sourceBackupExecutor.run(currentDeviceId, source);
                logger.info("Backup concluído para " + source.toAbsolutePath() + " snapshot=" + snapshotId);
            } catch (Exception e) {
                logger.error("Falha no backup da origem " + source.toAbsolutePath(), e);
            }
        }
    }

    private void authenticate() {
        if (config.auth() != null && config.auth().token() != null && !config.auth().token().isBlank()) {
            backend.setToken(config.auth().token());
            return;
        }

        backend.login(config.auth().email(), config.auth().password());
    }

    private void ensureDeviceRegistered() throws Exception {
        if (deviceId != null) {
            return;
        }

        String hostname = InetAddress.getLocalHost().getHostName();
        String deviceName = config.device() != null && config.device().name() != null && !config.device().name().isBlank()
                ? config.device().name()
                : hostname;

        deviceId = backend.registerDevice(deviceName, hostname, System.getProperty("os.name"), "0.1.0-daemon");
        logger.info("Device registrado: " + deviceId);
    }
}
