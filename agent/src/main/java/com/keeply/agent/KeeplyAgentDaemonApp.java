package com.keeply.agent;

import com.keeply.agent.config.AgentConfig;
import com.keeply.agent.config.AgentConfigLoader;
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
            Path dataDir = configPath.toAbsolutePath().getParent();
            if (dataDir == null) {
                throw new IllegalArgumentException("Não foi possível resolver diretório de dados para " + configPath);
            }
            Files.createDirectories(dataDir);
            Path dbPath = dataDir.resolve("agent.db");
            Path lockPath = dataDir.resolve("daemon.pid");

            AgentConfig config = new AgentConfigLoader().load(configPath);
            BackupCycleRunner runner = new BackupCycleRunner(config, dbPath, logger);

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
