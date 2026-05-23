package com.keeply.agent.model;

import java.time.Instant;
import java.util.List;

public record ProtectionPlan(PlanType planType, List<String> sources, Instant updatedAt) {
    public enum PlanType {
        DEFAULT,
        CUSTOM
    }
}
