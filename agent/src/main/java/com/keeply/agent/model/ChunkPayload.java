package com.keeply.agent.model;

public record ChunkPayload(
        String hash,
        long originalSize,
        long compressedSize,
        byte[] compressedBytes
) {}
