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
            @NotBlank @Size(max = 128) String password
    ) {
    }

    public record DeviceLoginRequest(
            @NotBlank @Email @Size(max = 255) String email,
            @NotBlank @Size(max = 128) String password,
            @NotBlank @Size(max = 255) String deviceInstallationId,
            @NotBlank @Size(max = 255) String hostname,
            // VULN-012: osName e agentVersion são opcionais — agentes antigos podem não os enviar
            // nullableTrim() no AuthService já trata null/blank corretamente
            @Size(max = 100) String osName,
            @Size(max = 100) String agentVersion
    ) {
    }

    public record RefreshRequest(
            @NotBlank @Size(max = 4096) String refreshToken,
            @NotBlank @Size(max = 255) String deviceInstallationId
    ) {
    }

    public record AuthResponse(String accessToken, String refreshToken, UUID userId, String email, UUID deviceId) {
    }
}
