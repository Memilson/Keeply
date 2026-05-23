package com.keeply.backend.dto;

import java.util.List;

public final class ChunkDtos {
    private ChunkDtos() {}

    public record CheckChunksRequest(List<String> hashes) {}
    public record CheckChunksResponse(List<String> existing, List<String> missing) {}
    public record ChunkUploadResponse(String hash, boolean stored) {}
}
