package com.keeply.agent.model;

import java.util.UUID;

public record DeviceSession(
        String deviceInstallationId,
        UUID deviceId,
        String accessToken,
        String refreshToken,
        UUID userId,
        String email
) {
}
