package com.keeply.backend.dto;

import java.time.Instant;
import java.util.UUID;

public final class DeviceDtos {
    private DeviceDtos() {}

    public record RegisterDeviceRequest(String name, String hostname, String os, String agentVersion) {}
    public record DeviceResponse(UUID id, String name, String hostname, String os, String agentVersion, Instant lastSeenAt) {}
}
