package com.keeply.agent.model;

public record ChunkMetadata(
        String hash,
        long originalSize,
        long compressedSize
) {
}
