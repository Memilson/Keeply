package com.keeply.agent.model;

import java.time.Instant;
import java.util.List;

public record SnapshotManifest(
        Integer manifestVersion,
        String snapshotId,
        String sourcePath,
        Instant createdAt,
        String chunking,
        ChunkCompression chunkCompression,
        String hashAlgorithm,
        List<FileManifest> files
) {}
