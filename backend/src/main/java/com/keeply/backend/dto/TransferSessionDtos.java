package com.keeply.backend.dto;

import com.keeply.backend.model.TransferSessionType;

import java.time.Instant;
import java.util.UUID;

public final class TransferSessionDtos {
    private TransferSessionDtos() {
    }

    public record Credentials(
            UUID transferSessionId,
            TransferSessionType type,
            String bucket,
            String minioEndpoint,
            String accessKey,
            String secretKey,
            String sessionToken,
            Instant expiresAt,
            Instant renewAfter,
            String stagingPrefix
    ) {
    }

    public record FinishResponse(UUID transferSessionId, String status) {
    }
}
