package com.keeply.agent.model;

import java.time.Instant;
import java.util.List;

public record SnapshotManifest(
        String snapshotId,
        String sourcePath,
        Instant createdAt,
        String chunking,
        String compression,
        String hashAlgorithm,
        List<FileManifest> files
) {}
