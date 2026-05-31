package com.keeply.agent.model;

public record ChunkMetadata(
        String hash,
        long originalSize,
        long storedSize,
        String compressionAlgorithm,
        Integer compressionLevel
) {
    public ChunkMetadata {
        if (!"ZSTD".equalsIgnoreCase(compressionAlgorithm) || compressionLevel == null || compressionLevel != 3) {
            throw new IllegalArgumentException("Chunks devem usar ZSTD level 3");
        }
    }

    public ChunkMetadata(String hash, long originalSize, long storedSize) {
        this(hash, originalSize, storedSize, "ZSTD", 3);
    }
}
