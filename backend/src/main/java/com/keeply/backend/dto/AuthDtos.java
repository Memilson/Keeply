package com.keeply.backend.dto;

import java.util.UUID;

public final class AuthDtos {
    private AuthDtos() {
    }

    public record RegisterRequest(String name, String email, String password) {
    }

    public record LoginRequest(String email, String password) {
    }

    public record DeviceLoginRequest(
            String email,
            String password,
            String deviceInstallationId,
            String hostname,
            String osName,
            String agentVersion
    ) {
    }

    public record RefreshRequest(String refreshToken, String deviceInstallationId) {
    }

    public record AuthResponse(String accessToken, String refreshToken, UUID userId, String email, UUID deviceId) {
    }
}
