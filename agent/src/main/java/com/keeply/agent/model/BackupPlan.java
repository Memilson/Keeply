package com.keeply.agent.model;

import java.util.List;

public record BackupPlan(
        SnapshotManifest manifest,
        List<ChunkPayload> chunks
) {
    public long totalOriginalSize() {
        return manifest.files().stream().mapToLong(FileManifest::size).sum();
    }

    public long totalCompressedSize() {
        return chunks.stream().mapToLong(ChunkPayload::compressedSize).sum();
    }

    public long totalFiles() {
        return manifest.files().size();
    }
}
