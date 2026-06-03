/* DTOs utilizados nas requisições e respostas do ciclo de vida dos snapshots (início, conclusão, falha). */
package com.keeply.backend.dto;

import com.keeply.backend.model.SnapshotStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class SnapshotDtos {
    private SnapshotDtos() {}

    public record StartSnapshotRequest(
            @NotNull UUID deviceId,
            @NotBlank @Size(max = 4096) String sourcePath
    ) {}

    public record FailSnapshotRequest(@NotBlank @Size(max = 2000) String errorMessage) {}

    public record CompleteSnapshotRequest(
            @NotNull UUID transferSessionId,
            @PositiveOrZero long totalFiles,
            @PositiveOrZero long totalOriginalSize,
            @PositiveOrZero long totalCompressedSize
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

    public record PagedSnapshotResponse(
            List<SnapshotResponse> items,
            PageMetadata pagination
    ) {}

    public record SnapshotFileListResponse(
            List<SnapshotFileItem> items,
            PageMetadata pagination
    ) {}

    public record SelectedArchiveRequest(
            @NotNull @Size(min = 1, max = 10) List<@NotBlank @Size(max = 4096) String> paths
    ) {}
}
