package com.keeply.agent;

import com.keeply.agent.auth.DeviceAuthStore;
import com.keeply.agent.config.AgentConfig;
import com.keeply.agent.config.AgentConfigLoader;
import com.keeply.agent.core.LocalDatabase;
import com.keeply.agent.daemon.AgentPaths;
import com.keeply.agent.daemon.BackupCycleRunner;
import com.keeply.agent.daemon.CronScheduler;
import com.keeply.agent.daemon.DaemonLogger;
import com.keeply.agent.daemon.DaemonInstanceLock;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public final class KeeplyAgentDaemonApp {
    private KeeplyAgentDaemonApp() {
    }

    public static void main(String[] args) {
        DaemonLogger logger = new DaemonLogger();

        try {
            Path configPath = resolveConfigPath(args);
            
            // Garantir que todos os diretórios base existam
            Files.createDirectories(AgentPaths.resolveConfigDir());
            Files.createDirectories(AgentPaths.resolveDataDir());
            Files.createDirectories(AgentPaths.resolveStateDir());
            Files.createDirectories(AgentPaths.resolveRuntimeDir());
            
            Path dbPath = AgentPaths.resolveMainDbPath();
            Path lockPath = AgentPaths.resolvePidPath();

            LocalDatabase db = new LocalDatabase(dbPath.toAbsolutePath().toString());
            DeviceAuthStore authStore = new DeviceAuthStore(AgentPaths.resolveDeviceAuthPath());

            AgentConfig config = new AgentConfigLoader().load(configPath);
            BackupCycleRunner runner = new BackupCycleRunner(config, db, authStore, logger);

            logger.info("Iniciando daemon com config: " + configPath);
            try (DaemonInstanceLock lock = DaemonInstanceLock.acquire(lockPath)) {
                if (Boolean.TRUE.equals(config.schedule().runOnStartup())) {
                    logger.info("runOnStartup=true, executando ciclo imediato.");
                    runner.runCycle();
                } else {
                    logger.info("runOnStartup=false, aguardando próximo horário do cron.");
                }

                CronScheduler scheduler = new CronScheduler(config.schedule().cron(), runner::runCycle, logger);
                Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                    logger.info("Encerrando daemon...");
                    scheduler.shutdown();
                }));

                scheduler.start();
                Thread.currentThread().join();
            }
        } catch (Exception e) {
            logger.error("Falha fatal ao iniciar daemon", e);
            System.exit(1);
        }
    }

    private static Path resolveConfigPath(String[] args) {
        for (int i = 0; i < args.length; i++) {
            if ("--config".equals(args[i])) {
                if (i + 1 >= args.length) {
                    throw new IllegalArgumentException("Informe o caminho após --config");
                }
                return Paths.get(args[i + 1]).toAbsolutePath().normalize();
            }
        }

        return AgentPaths.resolveDefaultConfigPath();
    }
}
