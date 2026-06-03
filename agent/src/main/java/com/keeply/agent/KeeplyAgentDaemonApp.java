package com.keeply.agent;

import com.keeply.agent.api.BackendClient;
import com.keeply.agent.auth.DeviceAuthStore;
import com.keeply.agent.config.AgentConfig;
import com.keeply.agent.config.AgentConfigLoader;
import com.keeply.agent.core.LocalDatabase;
import com.keeply.agent.daemon.AgentPaths;
import com.keeply.agent.daemon.BackupCycleRunner;
import com.keeply.agent.daemon.CronScheduler;
import com.keeply.agent.daemon.DaemonInstanceLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class KeeplyAgentDaemonApp {
    private static final Logger log = LoggerFactory.getLogger(KeeplyAgentDaemonApp.class);
    private static final String VERSION = "0.1.0";

    private KeeplyAgentDaemonApp() {
    }

    public static void main(String[] args) {
        log.info("event=daemon.boot app=keeply-agent-daemon version={}", VERSION);
        log.info("event=daemon.runtime max_heap_bytes={} chunk_upload_workers=4 chunk_upload_queue_size=4",
                Runtime.getRuntime().maxMemory());

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
            BackendClient backend = new BackendClient(config.backend().url(), authStore);
            BackupCycleRunner runner = new BackupCycleRunner(config, configPath, db, authStore);

            log.info("event=daemon.start config_path={}", configPath);
            try (DaemonInstanceLock lock = DaemonInstanceLock.acquire(lockPath)) {
                if (config.schedule() != null && Boolean.TRUE.equals(config.schedule().runOnStartup())) {
                    log.info("event=daemon.startup_run enabled=true action=run_cycle_now");
                    runner.runCycle();
                } else {
                    log.info("event=daemon.startup_run enabled=false action=wait_cron");
                }

                CronScheduler scheduler = null;
                String cron = config.schedule() != null ? config.schedule().cron() : null;
                if (cron != null && !cron.isBlank()) {
                    scheduler = new CronScheduler(cron, runner::runCycle);
                } else {
                    log.warn("event=daemon.schedule status=disabled reason=missing_schedule_cron");
                }

                ScheduledExecutorService heartbeatExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
                    Thread t = new Thread(r, "keeply-heartbeat");
                    t.setDaemon(true);
                    return t;
                });
                heartbeatExecutor.scheduleAtFixedRate(() -> {
                    try {
                        backend.refreshSession();
                        var session = backend.getSession();
                        if (session != null && session.deviceId() != null) {
                            backend.heartbeat(session.deviceId());
                            log.debug("event=heartbeat status=ok device_id={}", session.deviceId());
                        }
                    } catch (Exception e) {
                        log.warn("event=heartbeat status=failed message={}", e.getMessage());
                    }
                }, 5, 5, TimeUnit.MINUTES);

                CronScheduler finalScheduler = scheduler;
                Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                    log.info("event=daemon.shutdown status=started");
                    heartbeatExecutor.shutdownNow();
                    if (finalScheduler != null) {
                        finalScheduler.shutdown();
                    }
                    log.info("event=daemon.shutdown status=completed");
                }));

                if (scheduler != null) {
                    scheduler.start();
                    Thread.currentThread().join();
                } else {
                    new java.util.concurrent.CountDownLatch(1).await();
                }
            }
        } catch (Exception e) {
            log.error("event=daemon.boot status=fatal_error", e);
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
