package com.keeply.agent.model;

import java.time.Instant;

public record LocalPlanState(
        ProtectionPlan plan,
        Instant localUpdatedAt,
        Instant lastRemoteUpdatedAt,
        String encryptionPassword
) {
}
