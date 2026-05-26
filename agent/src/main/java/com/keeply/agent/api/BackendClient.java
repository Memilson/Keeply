package com.keeply.agent.api;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.keeply.agent.model.ChunkPayload;
import com.keeply.agent.model.DeviceSession;
import com.keeply.agent.model.ProtectionPlan;
import com.keeply.agent.model.SnapshotSummary;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public class BackendClient {
    private static final Logger logger = LoggerFactory.getLogger(BackendClient.class);
    private static final String TRACE_ID_HEADER = "X-Trace-Id";

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(java.time.Duration.ofSeconds(15))
            .build();
    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule()).findAndRegisterModules();
    private final String baseUrl;
    private volatile DeviceSession session;

    public BackendClient(String baseUrl) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    public synchronized DeviceSession loginDevice(String email, String password, String deviceInstallationId, String hostname, String osName, String agentVersion) {
        String traceId = UUID.randomUUID().toString();
        try {
            String body = mapper.writeValueAsString(Map.of(
                    "email", email,
                    "password", password,
                    "deviceInstallationId", deviceInstallationId,
                    "hostname", hostname,
                    "osName", osName,
                    "agentVersion", agentVersion
            ));
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/auth/login-device"))
                    .timeout(java.time.Duration.ofSeconds(30))
                    .header("Content-Type", "application/json")
                    .header(TRACE_ID_HEADER, traceId)
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = sendAndLog(request, traceId);
            require2xx(response);
            DeviceSession deviceSession = parseAuthResponse(response.body(), deviceInstallationId);
            this.session = deviceSession;
            return deviceSession;
        } catch (Exception e) {
            throw new IllegalStateException("Falha no login do device [Trace-ID: %s]".formatted(traceId), e);
        }
    }

    public synchronized void setSession(DeviceSession session) {
        this.session = session;
    }

    public DeviceSession getSession() {
        return session;
    }

    public synchronized DeviceSession refreshSession() {
        if (session == null || blank(session.refreshToken()) || blank(session.deviceInstallationId())) {
            throw new IllegalStateException("Sessão inválida para refresh");
        }

        String traceId = UUID.randomUUID().toString();
        try {
            String body = mapper.writeValueAsString(Map.of(
                    "refreshToken", session.refreshToken(),
                    "deviceInstallationId", session.deviceInstallationId()
            ));
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/auth/refresh"))
                    .timeout(java.time.Duration.ofSeconds(30))
                    .header("Content-Type", "application/json")
                    .header(TRACE_ID_HEADER, traceId)
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = sendAndLog(request, traceId);
            require2xx(response);
            DeviceSession refreshed = parseAuthResponse(response.body(), session.deviceInstallationId());
            this.session = refreshed;
            return refreshed;
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao renovar sessão [Trace-ID: %s]".formatted(traceId), e);
        }
    }

    public Optional<ProtectionPlan> getDevicePlan(UUID deviceId) {
        String traceId = UUID.randomUUID().toString();
        try {
            HttpRequest request = authorized("/api/devices/" + deviceId + "/plan", traceId).GET().build();
            HttpResponse<String> response = sendWithRefreshRetry(request, traceId);
            if (response.statusCode() == 404) {
                return Optional.empty();
            }
            require2xx(response);
            return Optional.of(mapper.readValue(response.body(), ProtectionPlan.class));
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao obter plano do device [Trace-ID: %s]".formatted(traceId), e);
        }
    }

    public ProtectionPlan upsertDevicePlan(UUID deviceId, ProtectionPlan.PlanType planType, List<String> sources) {
        String traceId = UUID.randomUUID().toString();
        try {
            String body = mapper.writeValueAsString(Map.of("planType", planType, "sources", sources));
            HttpResponse<String> response = sendJson("/api/devices/" + deviceId + "/plan", body, "PUT", traceId);
            return mapper.readValue(response.body(), ProtectionPlan.class);
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao salvar plano do device [Trace-ID: %s]".formatted(traceId), e);
        }
    }

    public UUID startSnapshot(UUID deviceId, String sourcePath) {
        String traceId = UUID.randomUUID().toString();
        try {
            String body = mapper.writeValueAsString(Map.of("deviceId", deviceId.toString(), "sourcePath", sourcePath));
            HttpResponse<String> response = sendJson("/api/snapshots/start", body, "POST", traceId);
            Map<String, Object> json = mapper.readValue(response.body(), new TypeReference<>() {});
            return UUID.fromString((String) json.get("id"));
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao iniciar snapshot [Trace-ID: %s]".formatted(traceId), e);
        }
    }

    public Set<String> checkChunks(List<String> hashes) {
        String traceId = UUID.randomUUID().toString();
        try {
            String body = mapper.writeValueAsString(Map.of("hashes", hashes));
            HttpResponse<String> response = sendJson("/api/chunks/check", body, "POST", traceId);
            Map<String, List<String>> json = mapper.readValue(response.body(), new TypeReference<>() {});
            return new HashSet<>(json.getOrDefault("existing", List.of()));
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao verificar chunks [Trace-ID: %s]".formatted(traceId), e);
        }
    }

    public BatchUploadStats uploadChunksBatch(List<ChunkPayload> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return new BatchUploadStats(0, 0, 0, 0);
        }
        String traceId = UUID.randomUUID().toString();
        try {
            List<Map<String, Object>> items = chunks.stream().map(chunk -> {
                Map<String, Object> item = new java.util.LinkedHashMap<>();
                item.put("hash", chunk.hash());
                item.put("originalSize", chunk.originalSize());
                item.put("compressedSize", chunk.compressedSize());
                item.put("compressedBytesBase64", Base64.getEncoder().encodeToString(chunk.compressedBytes()));
                return item;
            }).toList();
            String body = mapper.writeValueAsString(Map.of("items", items));
            HttpResponse<String> response = sendJson("/api/chunks/upload-batch", body, "POST", traceId);
            Map<String, Object> json = mapper.readValue(response.body(), new TypeReference<>() {});
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> results = (List<Map<String, Object>>) json.getOrDefault("results", List.of());

            int stored = 0;
            int duplicates = 0;
            int failed = 0;
            for (Map<String, Object> result : results) {
                boolean storedItem = Boolean.TRUE.equals(result.get("stored"));
                String error = result.get("error") == null ? null : result.get("error").toString();
                if (storedItem) {
                    stored++;
                } else if (error == null || error.isBlank()) {
                    duplicates++;
                } else {
                    failed++;
                    String hash = result.get("hash") == null ? "desconhecido" : result.get("hash").toString();
                    throw new IllegalStateException("Falha no chunk %s: %s".formatted(hash, error));
                }
            }
            return new BatchUploadStats(results.size(), stored, duplicates, failed);
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao enviar lote de chunks [Trace-ID: %s]".formatted(traceId), e);
        }
    }

    public void completeSnapshot(UUID snapshotId, String manifestJson, long totalFiles, long totalOriginalSize, long totalCompressedSize) {
        String traceId = UUID.randomUUID().toString();
        try {
            String body = mapper.writeValueAsString(Map.of(
                    "manifestJson", manifestJson,
                    "totalFiles", totalFiles,
                    "totalOriginalSize", totalOriginalSize,
                    "totalCompressedSize", totalCompressedSize
            ));
            sendJson("/api/snapshots/" + snapshotId + "/complete", body, "POST", traceId);
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao concluir snapshot %s [Trace-ID: %s]".formatted(snapshotId, traceId), e);
        }
    }

    public void failSnapshot(UUID snapshotId, String errorMessage) {
        String traceId = UUID.randomUUID().toString();
        try {
            String body = mapper.writeValueAsString(Map.of("errorMessage", errorMessage));
            sendJson("/api/snapshots/" + snapshotId + "/fail", body, "POST", traceId);
        } catch (Exception ignored) {
        }
    }

    public List<SnapshotSummary> listSnapshots() {
        String traceId = UUID.randomUUID().toString();
        try {
            HttpRequest request = authorized("/api/snapshots", traceId).GET().build();
            HttpResponse<String> response = sendWithRefreshRetry(request, traceId);
            require2xx(response);
            return mapper.readValue(response.body(), new TypeReference<>() {});
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao listar snapshots [Trace-ID: %s]".formatted(traceId), e);
        }
    }

    public String downloadManifest(UUID snapshotId) {
        String traceId = UUID.randomUUID().toString();
        try {
            HttpRequest request = authorized("/api/snapshots/" + snapshotId + "/manifest", traceId).GET().build();
            HttpResponse<String> response = sendWithRefreshRetry(request, traceId);
            require2xx(response);
            return response.body();
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao baixar manifesto [Trace-ID: %s]".formatted(traceId), e);
        }
    }

    public byte[] downloadChunk(String hash) {
        String traceId = UUID.randomUUID().toString();
        try {
            HttpRequest request = authorized("/api/chunks/" + hash + "/download", traceId).GET().build();
            HttpResponse<byte[]> response = sendBytesWithRefreshRetry(request, traceId);
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("HTTP " + response.statusCode());
            }
            return response.body();
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao baixar chunk %s [Trace-ID: %s]".formatted(hash, traceId), e);
        }
    }

    private DeviceSession parseAuthResponse(String responseBody, String installationId) throws Exception {
        Map<String, Object> json = mapper.readValue(responseBody, new TypeReference<>() {});
        String accessToken = (String) json.get("accessToken");
        String refreshToken = (String) json.get("refreshToken");
        UUID userId = json.get("userId") == null ? null : UUID.fromString((String) json.get("userId"));
        UUID deviceId = json.get("deviceId") == null ? null : UUID.fromString((String) json.get("deviceId"));
        String email = (String) json.get("email");
        return new DeviceSession(installationId, deviceId, accessToken, refreshToken, userId, email);
    }

    private HttpResponse<String> sendJson(String path, String body, String method, String traceId) throws Exception {
        HttpRequest request = authorized(path, traceId)
                .header("Content-Type", "application/json")
                .method(method, HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> response = sendWithRefreshRetry(request, traceId);
        require2xx(response);
        return response;
    }

    private HttpRequest.Builder authorized(String path, String traceId) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .header(TRACE_ID_HEADER, traceId)
                .timeout(java.time.Duration.ofSeconds(60));
        if (session != null && !blank(session.accessToken())) {
            builder.header("Authorization", "Bearer " + session.accessToken());
        }
        return builder;
    }

    private HttpResponse<String> sendWithRefreshRetry(HttpRequest request, String traceId) throws Exception {
        HttpResponse<String> response = sendAndLog(request, traceId);
        if (response.statusCode() == 401 && session != null && !blank(session.refreshToken())) {
            logger.info("[{}] Recebido 401, tentando refresh session", traceId);
            refreshSession();
            HttpRequest retry = retryWithUpdatedAccessToken(request);
            response = sendAndLog(retry, traceId);
            if (response.statusCode() == 401) {
                throw new IllegalStateException("Sessão expirada ou revogada. Faça login novamente.");
            }
        }
        return response;
    }

    private HttpResponse<byte[]> sendBytesWithRefreshRetry(HttpRequest request, String traceId) throws Exception {
        HttpResponse<byte[]> response = sendAndLogBytes(request, traceId);
        if (response.statusCode() == 401 && session != null && !blank(session.refreshToken())) {
            logger.info("[{}] Recebido 401, tentando refresh session", traceId);
            refreshSession();
            HttpRequest retry = retryWithUpdatedAccessToken(request);
            response = sendAndLogBytes(retry, traceId);
            if (response.statusCode() == 401) {
                throw new IllegalStateException("Sessão expirada ou revogada. Faça login novamente.");
            }
        }
        return response;
    }

    private HttpResponse<String> sendAndLog(HttpRequest request, String traceId) throws Exception {
        logger.debug("[{}] Request: {} {}", traceId, request.method(), request.uri());
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        logger.debug("[{}] Response: {} ({} bytes)", traceId, response.statusCode(), response.body().length());
        return response;
    }

    private HttpResponse<byte[]> sendAndLogBytes(HttpRequest request, String traceId) throws Exception {
        logger.debug("[{}] Request: {} {}", traceId, request.method(), request.uri());
        HttpResponse<byte[]> response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
        logger.debug("[{}] Response: {} ({} bytes)", traceId, response.statusCode(), response.body().length);
        return response;
    }

    private HttpRequest retryWithUpdatedAccessToken(HttpRequest original) {
        HttpRequest.Builder builder = HttpRequest.newBuilder().uri(original.uri());
        original.headers().map().forEach((k, values) -> {
            if (!"authorization".equalsIgnoreCase(k)) {
                for (String value : values) {
                    builder.header(k, value);
                }
            }
        });
        if (session != null && !blank(session.accessToken())) {
            builder.header("Authorization", "Bearer " + session.accessToken());
        }
        builder.method(original.method(), original.bodyPublisher().orElse(HttpRequest.BodyPublishers.noBody()));
        return builder.build();
    }

    private void require2xx(HttpResponse<String> response) {
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            String message = "HTTP " + response.statusCode();
            try {
                Map<String, Object> errorMap = mapper.readValue(response.body(), new TypeReference<>() {});
                if (errorMap.containsKey("error")) {
                    message = errorMap.get("error").toString();
                }
            } catch (Exception ignored) {
                if (response.body() != null && !response.body().isBlank()) {
                    message += ": " + response.body();
                }
            }
            throw new IllegalStateException(message);
        }
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    public record BatchUploadStats(int sent, int stored, int duplicate, int failed) {}
}
