package com.keeply.agent.config;

import com.keeply.agent.model.ProtectionPlan;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentConfigWriterTest {
    @TempDir
    Path tempDir;

    @Test
    void savePlanPersistsOfflineCacheFields() throws Exception {
        Path configPath = tempDir.resolve("agent.yaml");
        AgentConfigWriter writer = new AgentConfigWriter(configPath);
        AgentConfigReader reader = new AgentConfigReader(configPath);
        ProtectionPlan plan = new ProtectionPlan(
                ProtectionPlan.PlanType.CUSTOM,
                List.of("/data/a", "/data/b"),
                true,
                true,
                true,
                "0 4 * * *",
                ProtectionPlan.RetentionMode.KEEP_DAYS,
                15,
                Instant.parse("2026-06-08T12:00:00Z")
        );

        writer.savePlan("http://localhost:8080", "user@example.com", plan,
                Instant.parse("2026-06-08T12:05:00Z"),
                Instant.parse("2026-06-08T12:00:00Z"));

        AgentConfigReader.UiConfig saved = reader.read().orElseThrow();
        assertEquals("http://localhost:8080", saved.backendUrl());
        assertEquals("user@example.com", saved.email());
        assertEquals(List.of("/data/a", "/data/b"), saved.sources());
        assertEquals("0 4 * * *", saved.cron());
        assertTrue(saved.cdpEnabled());
        assertTrue(saved.validationEnabled());
        assertEquals("KEEP_DAYS", saved.retentionMode());
        assertEquals(15, saved.retentionDays());
        assertEquals(Instant.parse("2026-06-08T12:05:00Z"), saved.localUpdatedAt());
        assertEquals(Instant.parse("2026-06-08T12:00:00Z"), saved.lastRemoteUpdatedAt());
        assertTrue(configPath.toFile().isFile());
    }

    @Test
    void saveSchedulePersistsDailyCronFromSelectedTime() throws Exception {
        Path configPath = tempDir.resolve("agent.yaml");
        AgentConfigWriter writer = new AgentConfigWriter(configPath);
        AgentConfigReader reader = new AgentConfigReader(configPath);

        writer.saveSchedule("http://localhost:8080", "user@example.com", List.of("/data/a"), "0 4 * * *");

        AgentConfigReader.UiConfig saved = reader.read().orElseThrow();
        assertEquals("0 4 * * *", saved.cron());
        assertEquals(List.of("/data/a"), saved.sources());
        assertTrue(configPath.toFile().isFile());
    }
}
