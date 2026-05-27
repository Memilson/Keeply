package com.keeply.agent.api;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

public final class ChunkApiClient {
    private final HttpExecutor executor;
    private final ObjectMapper mapper;

    ChunkApiClient(HttpExecutor executor, ObjectMapper mapper) {
        this.executor = executor;
        this.mapper = mapper;
    }

    BackendClient.CheckChunksResult checkChunks(List<String> hashes, String traceId) throws Exception {
        String body = mapper.writeValueAsString(Map.of("hashes", hashes));
        return mapper.readValue(executor.sendJson("/api/chunks/check", body, "POST", traceId).body(),
                BackendClient.CheckChunksResult.class);
    }

    long getStorageUsedBytes(String traceId) throws Exception {
        Map<String, Long> usage = mapper.readValue(executor.get("/api/chunks/storage-usage", traceId).body(),
                new TypeReference<>() {});
        return usage.getOrDefault("usedBytes", 0L);
    }
}
