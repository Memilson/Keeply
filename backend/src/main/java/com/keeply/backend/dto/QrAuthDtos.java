package com.keeply.backend.dto;

import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.UUID;

public final class QrAuthDtos {
    private QrAuthDtos() {}

    public record QrTokenResponse(
        String token,
        Instant expiresAt
    ) {}

    public record QrExchangeRequest(
        @NotBlank String token
    ) {}
}
