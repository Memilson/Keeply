package com.keeply.agent.model;

import java.util.UUID;

public record SnapshotSummary(
        UUID id,
        UUID deviceId,
        String status,
        String sourcePath,
        long totalFiles,
        long totalOriginalSize,
        long totalCompressedSize
) {}
