package com.keeply.agent.daemon;

import com.keeply.agent.api.BackendClient;
import com.keeply.agent.config.AgentConfig;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

class BackupCycleRunnerTest {
    @Test
    void backupAllSourcesFallsBackToLocalConfigWhenRemotePlanIsOffline() {
        BackendClient backend = new BackendClient("http://localhost:8080") {
            @Override
            public Optional<com.keeply.agent.model.ProtectionPlan> getDevicePlan(UUID deviceId) {
                throw new RuntimeException("offline", new IOException("network down"));
            }
        };

        AgentConfig config = new AgentConfig(
                new AgentConfig.Backend("http://localhost:8080"),
                new AgentConfig.Auth("user@example.com"),
                null,
                new AgentConfig.Backup(List.of(Path.of("/cache/one"), Path.of("/cache/two"))),
                new AgentConfig.Schedule("0 3 * * *", false),
                new AgentConfig.Retention("KEEP_DAYS", 10),
                new AgentConfig.Validation(true),
                new AgentConfig.Cdp(false),
                new AgentConfig.Encryption(false, null),
                new AgentConfig.PlanSync(null, null)
        );
        List<Path> executedSources = new ArrayList<>();
        BackupCycleRunner runner = new BackupCycleRunner(
                config,
                backend,
                null,
                null,
                null,
                (deviceId, source) -> {
                    executedSources.add(source);
                    return UUID.randomUUID();
                }
        );

        runner.backupAllSources(UUID.randomUUID());

        assertEquals(List.of(Path.of("/cache/one"), Path.of("/cache/two")), executedSources);
    }

    @Test
    void backupAllSourcesSkipsWhenNoRemotePlanAndNoLocalCache() {
        BackendClient backend = new BackendClient("http://localhost:8080") {
            @Override
            public Optional<com.keeply.agent.model.ProtectionPlan> getDevicePlan(UUID deviceId) {
                return Optional.empty();
            }
        };

        AgentConfig config = new AgentConfig(
                new AgentConfig.Backend("http://localhost:8080"),
                null,
                null,
                new AgentConfig.Backup(List.of()),
                new AgentConfig.Schedule(null, false),
                new AgentConfig.Retention("KEEP_ALL", null),
                new AgentConfig.Validation(false),
                new AgentConfig.Cdp(false),
                new AgentConfig.Encryption(false, null),
                new AgentConfig.PlanSync(null, null)
        );
        List<Path> executedSources = new ArrayList<>();
        BackupCycleRunner runner = new BackupCycleRunner(
                config,
                backend,
                null,
                null,
                null,
                (deviceId, source) -> {
                    executedSources.add(source);
                    return UUID.randomUUID();
                }
        );

        runner.backupAllSources(UUID.randomUUID());

        assertEquals(List.of(), executedSources);
    }
}
