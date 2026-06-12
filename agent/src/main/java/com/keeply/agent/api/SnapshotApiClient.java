package com.keeply.agent.api;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.keeply.agent.model.SnapshotSummary;
import com.keeply.agent.model.StartedSnapshot;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class SnapshotApiClient {
    private final HttpExecutor executor;
    private final ObjectMapper mapper;

    SnapshotApiClient(HttpExecutor executor, ObjectMapper mapper) {
        this.executor = executor;
        this.mapper = mapper;
    }

    StartedSnapshot start(UUID deviceId, String sourcePath, String traceId) throws Exception {
        String body = mapper.writeValueAsString(Map.of("deviceId", deviceId.toString(), "sourcePath", sourcePath));
        return mapper.readValue(executor.sendJson(ApiEndpoints.SNAPSHOT_START, body, "POST", traceId).body(),
                StartedSnapshot.class);
    }

    void complete(UUID snapshotId, UUID transferSessionId, long totalFiles, long totalOriginalSize,
                  long totalCompressedSize, String traceId) throws Exception {
        String body = mapper.writeValueAsString(Map.of(
                "transferSessionId", transferSessionId.toString(),
                "totalFiles", totalFiles,
                "totalOriginalSize", totalOriginalSize,
                "totalCompressedSize", totalCompressedSize));
        executor.sendJson(ApiEndpoints.snapshotComplete(snapshotId), body, "POST", traceId);
    }

    void fail(UUID snapshotId, String errorMessage, String traceId) throws Exception {
        executor.sendJson(ApiEndpoints.snapshotFail(snapshotId),
                mapper.writeValueAsString(Map.of("errorMessage", errorMessage)), "POST", traceId);
    }

    SnapshotSummary get(UUID snapshotId, String traceId) throws Exception {
        return mapper.readValue(executor.get(ApiEndpoints.snapshot(snapshotId), traceId).body(), SnapshotSummary.class);
    }

    List<SnapshotSummary> list(String traceId) throws Exception {
        List<SnapshotSummary> all = new java.util.ArrayList<>();
        int page = 0;
        while (true) {
            String path = ApiEndpoints.SNAPSHOTS + "?page=" + page + "&size=200";
            BackendClient.SnapshotPage result = mapper.readValue(
                    executor.get(path, traceId).body(), BackendClient.SnapshotPage.class);
            all.addAll(result.items());
            if (result.items().isEmpty() || all.size() >= result.pagination().totalElements()) break;
            page++;
        }
        return all;
    }

    void delete(UUID snapshotId, String traceId) throws Exception {
        executor.delete(ApiEndpoints.snapshot(snapshotId), traceId);
    }

    BackendClient.SnapshotFilePage listFiles(UUID snapshotId, int page, int size, String search,
                                             String prefix, String traceId) throws Exception {
        StringBuilder path = new StringBuilder(ApiEndpoints.snapshotFiles(snapshotId))
                .append("?page=").append(page).append("&size=").append(size);
        appendQuery(path, "search", search);
        appendQuery(path, "prefix", prefix);
        return mapper.readValue(executor.get(path.toString(), traceId).body(),
                BackendClient.SnapshotFilePage.class);
    }

    private static void appendQuery(StringBuilder path, String name, String value) {
        if (value != null && !value.isBlank()) {
            path.append('&').append(name).append('=')
                    .append(URLEncoder.encode(value, StandardCharsets.UTF_8));
        }
    }
}
