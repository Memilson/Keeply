package com.keeply.agent.config;

import com.keeply.agent.api.BackendClient;
import com.keeply.agent.model.ProtectionPlan;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProtectionPlanSyncServiceTest {
    @TempDir
    Path tempDir;

    @Test
    void reconcileUsesRemotePlanWhenBackendUpdatedAtIsNewer() throws Exception {
        Path configPath = tempDir.resolve("agent.yaml");
        AgentConfigWriter writer = new AgentConfigWriter(configPath);
        AgentConfigReader reader = new AgentConfigReader(configPath);
        writer.savePlan("http://localhost:8080", "user@example.com", plan(List.of("/local"), false, false,
                        "0 2 * * *", Instant.parse("2026-06-08T10:00:00Z")),
                Instant.parse("2026-06-08T10:00:00Z"),
                Instant.parse("2026-06-08T10:00:00Z"));

        ProtectionPlan remotePlan = plan(List.of("/remote"), true, true,
                "0 5 * * *", Instant.parse("2026-06-08T11:00:00Z"));
        BackendClient backend = new StubBackendClient(remotePlan, null, false);
        ProtectionPlanSyncService service = new ProtectionPlanSyncService(backend, reader, writer);

        ProtectionPlanSyncService.ReconciledPlan reconciled = service.reconcile(
                UUID.randomUUID(), "http://localhost:8080", "user@example.com");

        assertEquals(List.of("/remote"), reconciled.plan().sources());
        assertTrue(reconciled.plan().validationEnabled());
        AgentConfigReader.UiConfig saved = reader.read().orElseThrow();
        assertEquals(List.of("/remote"), saved.sources());
        assertTrue(saved.validationEnabled());
        assertEquals(Instant.parse("2026-06-08T11:00:00Z"), saved.localUpdatedAt());
    }

    @Test
    void reconcilePushesLocalPlanWhenLocalUpdatedAtIsNewer() throws Exception {
        Path configPath = tempDir.resolve("agent.yaml");
        AgentConfigWriter writer = new AgentConfigWriter(configPath);
        AgentConfigReader reader = new AgentConfigReader(configPath);
        ProtectionPlan localPlan = plan(List.of("/local"), true, true,
                "0 6 * * *", Instant.parse("2026-06-08T09:00:00Z"));
        writer.savePlan("http://localhost:8080", "user@example.com", localPlan,
                Instant.parse("2026-06-08T12:00:00Z"),
                Instant.parse("2026-06-08T09:00:00Z"));

        ProtectionPlan remotePlan = plan(List.of("/remote"), false, false,
                "0 2 * * *", Instant.parse("2026-06-08T11:00:00Z"));
        StubBackendClient backend = new StubBackendClient(remotePlan,
                plan(List.of("/local"), true, true, "0 6 * * *", Instant.parse("2026-06-08T12:01:00Z")), false);
        ProtectionPlanSyncService service = new ProtectionPlanSyncService(backend, reader, writer);

        ProtectionPlanSyncService.ReconciledPlan reconciled = service.reconcile(
                UUID.randomUUID(), "http://localhost:8080", "user@example.com");

        assertEquals(List.of("/local"), backend.lastUpsertedPlan.sources());
        assertTrue(backend.lastUpsertedPlan.validationEnabled());
        assertEquals(Instant.parse("2026-06-08T12:01:00Z"), reconciled.plan().updatedAt());
        AgentConfigReader.UiConfig saved = reader.read().orElseThrow();
        assertEquals(List.of("/local"), saved.sources());
        assertEquals(Instant.parse("2026-06-08T12:01:00Z"), saved.lastRemoteUpdatedAt());
    }

    @Test
    void reconcileFallsBackToLocalPlanWhenBackendIsOffline() throws Exception {
        Path configPath = tempDir.resolve("agent.yaml");
        AgentConfigWriter writer = new AgentConfigWriter(configPath);
        AgentConfigReader reader = new AgentConfigReader(configPath);
        writer.savePlan("http://localhost:8080", "user@example.com", plan(List.of("/offline"), false, true,
                        "0 4 * * *", null),
                Instant.parse("2026-06-08T12:00:00Z"),
                null);

        ProtectionPlanSyncService service = new ProtectionPlanSyncService(
                new StubBackendClient(null, null, true), reader, writer);

        ProtectionPlanSyncService.ReconciledPlan reconciled = service.reconcile(
                UUID.randomUUID(), "http://localhost:8080", "user@example.com");

        assertEquals(List.of("/offline"), reconciled.plan().sources());
        assertTrue(reconciled.syncPending());
    }

    private static ProtectionPlan plan(List<String> sources, boolean cdpEnabled, boolean validationEnabled,
                                       String cron, Instant updatedAt) {
        return new ProtectionPlan(
                sources.size() == 1 ? ProtectionPlan.PlanType.DEFAULT : ProtectionPlan.PlanType.CUSTOM,
                sources,
                cdpEnabled,
                validationEnabled,
                false,
                cron,
                ProtectionPlan.RetentionMode.KEEP_ALL,
                null,
                updatedAt);
    }

    private static final class StubBackendClient extends BackendClient {
        private final ProtectionPlan remotePlan;
        private final ProtectionPlan upsertResponse;
        private final boolean offline;
        private ProtectionPlan lastUpsertedPlan;

        private StubBackendClient(ProtectionPlan remotePlan, ProtectionPlan upsertResponse, boolean offline) {
            super("http://localhost:8080", null);
            this.remotePlan = remotePlan;
            this.upsertResponse = upsertResponse;
            this.offline = offline;
        }

        @Override
        public Optional<ProtectionPlan> getDevicePlan(UUID deviceId) {
            if (offline) {
                throw new RuntimeException("offline", new IOException("network down"));
            }
            return Optional.ofNullable(remotePlan);
        }

        @Override
        public ProtectionPlan upsertDevicePlan(UUID deviceId, ProtectionPlan.PlanType type, List<String> sources,
                                               boolean cdpEnabled, boolean validationEnabled,
                                               boolean encryptionEnabled, String scheduleCron,
                                               ProtectionPlan.RetentionMode retentionMode, Integer retentionDays) {
            lastUpsertedPlan = new ProtectionPlan(type, sources, cdpEnabled, validationEnabled, encryptionEnabled,
                    scheduleCron, retentionMode, retentionDays, remotePlan != null ? remotePlan.updatedAt() : null);
            return upsertResponse;
        }
    }
}
