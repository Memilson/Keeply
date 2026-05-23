package com.keeply.agent.model;

import java.time.Instant;
import java.util.UUID;

public record SnapshotSummary(
        UUID id,
        UUID deviceId,
        String status,
        String sourcePath,
        long totalFiles,
        long totalOriginalSize,
        long totalCompressedSize,
        Instant startedAt,
        Instant completedAt,
        String errorMessage
) {}
