package com.keeply.backend.dto;

import com.keeply.backend.model.SnapshotStatus;
import java.time.Instant;
import java.util.UUID;

public final class SnapshotDtos {
    private SnapshotDtos() {}

    public record StartSnapshotRequest(UUID deviceId, String sourcePath) {}

    public record CompleteSnapshotRequest(
            String manifestJson,
            long totalFiles,
            long totalOriginalSize,
            long totalCompressedSize
    ) {}

    public record FailSnapshotRequest(String errorMessage) {}

    public record SnapshotResponse(
            UUID id,
            UUID deviceId,
            SnapshotStatus status,
            String sourcePath,
            long totalFiles,
            long totalOriginalSize,
            long totalCompressedSize,
            Instant startedAt,
            Instant completedAt,
            String errorMessage
    ) {}
}
