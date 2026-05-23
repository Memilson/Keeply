package com.keeply.agent.daemon;

import com.keeply.agent.config.AgentConfig;
import com.keeply.agent.auth.DeviceAuthStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BackupCycleRunnerTest {
    @TempDir
    Path tempDir;

    @Test
    void continuesWhenOneSourceFails() {
        List<Path> processed = new ArrayList<>();
        Path first = Path.of("/tmp/one");
        Path second = Path.of("/tmp/two");

        AgentConfig config = new AgentConfig(
                new AgentConfig.Backend("http://localhost:8080"),
                new AgentConfig.Auth("u.com", "secret"),
                new AgentConfig.Device("device"),
                new AgentConfig.Backup(List.of(first, second)),
                new AgentConfig.Schedule("*/5 * * * *", false)
        );

        DeviceAuthStore authStore = new DeviceAuthStore(tempDir.resolve("auth.json"));
        BackupCycleRunner runner = new BackupCycleRunner(config, authStore, new DaemonLogger(), (deviceId, source) -> {
            processed.add(source);
            if (source.equals(first)) {
                throw new IllegalStateException("boom");
            }
            return UUID.randomUUID();
        });

        runner.backupAllSources(UUID.randomUUID(), List.of(first, second));

        assertEquals(List.of(first, second), processed);
    }
}
