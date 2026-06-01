package com.keeply.agent.model;

import java.time.Instant;
import java.util.List;

public record ProtectionPlan(
        PlanType planType,
        List<String> sources,
        boolean cdpEnabled,
        boolean encryptionEnabled,
        String scheduleCron,
        Instant updatedAt
) {
    public enum PlanType {
        DEFAULT,
        CUSTOM
    }
}
