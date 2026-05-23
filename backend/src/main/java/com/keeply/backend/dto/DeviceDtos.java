package com.keeply.backend.dto;

import com.keeply.backend.model.PlanType;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class DeviceDtos {
    private DeviceDtos() {
    }

    public record RegisterDeviceRequest(String name, String hostname, String osName, String agentVersion) {
    }

    public record DeviceResponse(UUID id, String name, String hostname, String osName, String agentVersion, Instant lastSeenAt) {
    }

    public record PlanRequest(PlanType planType, List<String> sources) {
    }

    public record PlanResponse(PlanType planType, List<String> sources, Instant updatedAt) {
    }
}
