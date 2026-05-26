package com.keeply.agent.model;

import java.time.Instant;
import java.util.UUID;

public record TransferCredentials(
        UUID transferSessionId,
        String type,
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
