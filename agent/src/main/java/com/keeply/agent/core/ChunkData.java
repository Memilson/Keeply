package com.keeply.agent.core;

public record ChunkData(
        int index,
        long offset,
        byte[] data,
        int originalSize
) {}
