package com.keeply.agent.model;

import java.time.Instant;
import java.util.List;

public record ProtectionPlan(
        PlanType planType,
        List<String> sources,
        boolean cdpEnabled,
        boolean validationEnabled,
        boolean encryptionEnabled,
        String scheduleCron,
        RetentionMode retentionMode,
        Integer retentionDays,
        Instant updatedAt
) {
    public enum PlanType {
        DEFAULT,
        CUSTOM
    }

    public enum RetentionMode {
        KEEP_ALL,
        KEEP_DAYS
    }
}
