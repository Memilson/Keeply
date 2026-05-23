package com.keeply.backend.dto;

import java.time.Instant;
import java.util.List;

public final class ManifestParsingDtos {
    private ManifestParsingDtos() {}

    public record SnapshotManifest(
            String snapshotId,
            String sourcePath,
            Instant createdAt,
            String chunking,
            String compression,
            String hashAlgorithm,
            List<FileManifest> files
    ) {}

    public record FileManifest(
            String path,
            long size,
            Instant lastModified,
            String sha256,
            List<ManifestChunk> chunks
    ) {}

    public record ManifestChunk(
            int index,
            String hash,
            long originalSize,
            long compressedSize
    ) {}
}
