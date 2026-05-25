package com.keeply.backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public final class AuthDtos {
    private AuthDtos() {
    }

    public record RegisterRequest(
            @NotBlank @Size(max = 100) String name,
            @NotBlank @Email @Size(max = 255) String email,
            @NotBlank @Size(min = 8, max = 128) String password
    ) {
    }

    public record LoginRequest(
            @NotBlank @Email @Size(max = 255) String email,
            @NotBlank String password
    ) {
    }

    public record DeviceLoginRequest(
            @NotBlank @Email @Size(max = 255) String email,
            @NotBlank String password,
            @NotBlank String deviceInstallationId,
            @NotBlank String hostname,
            @NotBlank String osName,
            @NotBlank String agentVersion
    ) {
    }

    public record RefreshRequest(
            @NotBlank String refreshToken,
            @NotBlank String deviceInstallationId
    ) {
    }

    public record AuthResponse(String accessToken, String refreshToken, UUID userId, String email, UUID deviceId) {
    }
}
