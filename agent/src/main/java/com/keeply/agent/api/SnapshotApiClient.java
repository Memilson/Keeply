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
        return mapper.readValue(executor.sendJson("/api/snapshots/start", body, "POST", traceId).body(),
                StartedSnapshot.class);
    }

    void complete(UUID snapshotId, UUID transferSessionId, long totalFiles, long totalOriginalSize,
                  long totalCompressedSize, String traceId) throws Exception {
        String body = mapper.writeValueAsString(Map.of(
                "transferSessionId", transferSessionId.toString(),
                "totalFiles", totalFiles,
                "totalOriginalSize", totalOriginalSize,
                "totalCompressedSize", totalCompressedSize));
        executor.sendJson("/api/snapshots/" + snapshotId + "/complete", body, "POST", traceId);
    }

    void fail(UUID snapshotId, String errorMessage, String traceId) throws Exception {
        executor.sendJson("/api/snapshots/" + snapshotId + "/fail",
                mapper.writeValueAsString(Map.of("errorMessage", errorMessage)), "POST", traceId);
    }

    List<SnapshotSummary> list(String traceId) throws Exception {
        return mapper.readValue(executor.get("/api/snapshots", traceId).body(), new TypeReference<>() {});
    }

    BackendClient.SnapshotFilePage listFiles(UUID snapshotId, int page, int size, String search,
                                             String prefix, String traceId) throws Exception {
        StringBuilder path = new StringBuilder("/api/snapshots/").append(snapshotId)
                .append("/files?page=").append(page).append("&size=").append(size);
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
