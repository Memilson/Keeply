package com.keeply.agent.model;

public record ChunkCompression(
        String algorithm,
        Integer level
) {}
