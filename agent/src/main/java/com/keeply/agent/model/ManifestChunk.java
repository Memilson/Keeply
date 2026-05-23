package com.keeply.agent.model;

public record ManifestChunk(
        int index,
        String hash,
        long originalSize,
        long compressedSize
) {}
