/* DTOs utilizados nas requisições e respostas do ciclo de vida dos snapshots (início, conclusão, falha). */
package com.keeply.backend.dto;

import com.keeply.backend.model.SnapshotStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class SnapshotDtos {
    private SnapshotDtos() {}

    public record StartSnapshotRequest(UUID deviceId, String sourcePath) {}

    public record FailSnapshotRequest(String errorMessage) {}

    public record CompleteSnapshotRequest(
            UUID transferSessionId,
            long totalFiles,
            long totalOriginalSize,
            long totalCompressedSize
    ) {}

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

    public record StartSnapshotResponse(
            SnapshotResponse snapshot,
            TransferSessionDtos.Credentials transfer
    ) {}

    public record SnapshotFileItem(
            String path,
            long size,
            Instant lastModified
    ) {}

    public record PageMetadata(
            long totalElements,
            int page,
            int size
    ) {}

    public record SnapshotFileListResponse(
            List<SnapshotFileItem> items,
            PageMetadata pagination
    ) {}
}
