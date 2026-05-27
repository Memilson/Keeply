package com.keeply.backend.dto;

import com.keeply.backend.model.PlanType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class DeviceDtos {
    private DeviceDtos() {
    }

    public record RegisterDeviceRequest(
            @Size(max = 100) String name,
            @NotBlank @Size(max = 255) String hostname,
            @Size(max = 100) String osName,
            @Size(max = 100) String agentVersion
    ) {
    }

    public record DeviceResponse(UUID id, String name, String hostname, String osName, String agentVersion, Instant lastSeenAt) {
    }

    public record PlanRequest(
            @NotNull PlanType planType,
            @NotEmpty @Size(max = 100) List<@NotBlank @Size(max = 255) String> sources
    ) {
    }

    public record PlanResponse(PlanType planType, List<String> sources, Instant updatedAt) {
    }
}
