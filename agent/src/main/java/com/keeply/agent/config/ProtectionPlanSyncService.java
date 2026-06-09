package com.keeply.agent.config;

import com.keeply.agent.api.BackendClient;
import com.keeply.agent.model.LocalPlanState;
import com.keeply.agent.model.ProtectionPlan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class ProtectionPlanSyncService {
    private static final Logger log = LoggerFactory.getLogger(ProtectionPlanSyncService.class);

    private final BackendClient backend;
    private final AgentConfigReader configReader;
    private final AgentConfigWriter configWriter;

    public ProtectionPlanSyncService(BackendClient backend, AgentConfigReader configReader, AgentConfigWriter configWriter) {
        this.backend = backend;
        this.configReader = configReader;
        this.configWriter = configWriter;
    }

    public LocalPlanState saveLocalPlan(String backendUrl, String email, ProtectionPlan plan) throws Exception {
        Instant updatedAt = Instant.now();
        LocalPlanState state = new LocalPlanState(plan, updatedAt, plan.updatedAt(), null);
        configWriter.saveLocalPlanState(backendUrl, email, state);
        return state;
    }

    public ReconciledPlan reconcile(UUID deviceId, String backendUrl, String email) throws Exception {
        Optional<LocalPlanState> localState = configReader.readLocalPlanState();
        Optional<ProtectionPlan> remotePlan;
        try {
            remotePlan = backend.getDevicePlan(deviceId);
        } catch (RuntimeException e) {
            if (isNetworkError(e)) {
                return localState
                        .map(state -> new ReconciledPlan(state.plan(), PlanSource.LOCAL, true))
                        .orElse(new ReconciledPlan(null, PlanSource.NONE, true));
            }
            throw e;
        }

        if (remotePlan.isEmpty() && localState.isEmpty()) {
            return new ReconciledPlan(null, PlanSource.NONE, false);
        }

        if (remotePlan.isPresent() && localState.isEmpty()) {
            persistRemotePlan(backendUrl, email, remotePlan.get());
            return new ReconciledPlan(remotePlan.get(), PlanSource.REMOTE, false);
        }

        if (remotePlan.isEmpty()) {
            ProtectionPlan pushed = pushLocalPlan(deviceId, localState.orElseThrow());
            persistRemotePlan(backendUrl, email, pushed);
            return new ReconciledPlan(pushed, PlanSource.LOCAL, false);
        }

        LocalPlanState local = localState.orElseThrow();
        ProtectionPlan remote = remotePlan.orElseThrow();
        Instant remoteUpdatedAt = remote.updatedAt();
        Instant localUpdatedAt = local.localUpdatedAt();

        if (remoteUpdatedAt != null && (localUpdatedAt == null || remoteUpdatedAt.isAfter(localUpdatedAt))) {
            persistRemotePlan(backendUrl, email, remote);
            return new ReconciledPlan(remote, PlanSource.REMOTE, false);
        }

        if (localUpdatedAt != null && (remoteUpdatedAt == null || localUpdatedAt.isAfter(remoteUpdatedAt))) {
            ProtectionPlan pushed = pushLocalPlan(deviceId, local);
            persistRemotePlan(backendUrl, email, pushed);
            return new ReconciledPlan(pushed, PlanSource.LOCAL, false);
        }

        if (!plansEquivalent(remote, local.plan())) {
            persistRemotePlan(backendUrl, email, remote);
            return new ReconciledPlan(remote, PlanSource.REMOTE, false);
        }

        persistRemotePlan(backendUrl, email, remote);
        return new ReconciledPlan(remote, PlanSource.REMOTE, false);
    }

    private void persistRemotePlan(String backendUrl, String email, ProtectionPlan plan) throws Exception {
        LocalPlanState state = new LocalPlanState(plan, plan.updatedAt(), plan.updatedAt(), null);
        configWriter.saveLocalPlanState(backendUrl, email, state);
    }

    private ProtectionPlan pushLocalPlan(UUID deviceId, LocalPlanState state) {
        ProtectionPlan localPlan = state.plan();
        ProtectionPlan.PlanType planType = localPlan.planType() != null
                ? localPlan.planType()
                : inferPlanType(localPlan.sources());
        return backend.upsertDevicePlan(
                deviceId,
                planType,
                localPlan.sources(),
                localPlan.cdpEnabled(),
                localPlan.validationEnabled(),
                localPlan.encryptionEnabled(),
                localPlan.scheduleCron(),
                localPlan.retentionMode(),
                localPlan.retentionDays());
    }

    private static ProtectionPlan.PlanType inferPlanType(List<String> sources) {
        return sources != null && sources.size() == 1
                ? ProtectionPlan.PlanType.DEFAULT
                : ProtectionPlan.PlanType.CUSTOM;
    }

    private static boolean plansEquivalent(ProtectionPlan left, ProtectionPlan right) {
        return left.planType() == right.planType()
                && Objects.equals(left.sources(), right.sources())
                && left.cdpEnabled() == right.cdpEnabled()
                && left.validationEnabled() == right.validationEnabled()
                && left.encryptionEnabled() == right.encryptionEnabled()
                && Objects.equals(left.scheduleCron(), right.scheduleCron())
                && left.retentionMode() == right.retentionMode()
                && Objects.equals(left.retentionDays(), right.retentionDays());
    }

    private static boolean isNetworkError(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof IOException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    public record ReconciledPlan(ProtectionPlan plan, PlanSource source, boolean syncPending) {
    }

    public enum PlanSource {
        REMOTE,
        LOCAL,
        NONE
    }
}
