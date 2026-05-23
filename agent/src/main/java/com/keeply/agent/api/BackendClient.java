package com.keeply.agent.api;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.keeply.agent.model.ChunkPayload;
import com.keeply.agent.model.DeviceSession;
import com.keeply.agent.model.ProtectionPlan;
import com.keeply.agent.model.SnapshotSummary;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public class BackendClient {
    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule()).findAndRegisterModules();
    private final String baseUrl;
    private DeviceSession session;

    public BackendClient(String baseUrl) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    public DeviceSession loginDevice(String email, String password, String deviceInstallationId, String hostname, String osName, String agentVersion) {
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
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            require2xx(response);
            DeviceSession deviceSession = parseAuthResponse(response.body(), deviceInstallationId);
            this.session = deviceSession;
            return deviceSession;
        } catch (Exception e) {
            throw new IllegalStateException("Falha no login do device", e);
        }
    }

    public void setSession(DeviceSession session) {
        this.session = session;
    }

    public DeviceSession getSession() {
        return session;
    }

    public DeviceSession refreshSession() {
        if (session == null || blank(session.refreshToken()) || blank(session.deviceInstallationId())) {
            throw new IllegalStateException("Sessão inválida para refresh");
        }

        try {
            String body = mapper.writeValueAsString(Map.of(
                    "refreshToken", session.refreshToken(),
                    "deviceInstallationId", session.deviceInstallationId()
            ));
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/auth/refresh"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            require2xx(response);
            DeviceSession refreshed = parseAuthResponse(response.body(), session.deviceInstallationId());
            this.session = refreshed;
            return refreshed;
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao renovar sessão", e);
        }
    }

    public Optional<ProtectionPlan> getDevicePlan(UUID deviceId) {
        try {
            HttpRequest request = authorized("/api/devices/" + deviceId + "/plan").GET().build();
            HttpResponse<String> response = sendWithRefreshRetry(request);
            if (response.statusCode() == 404) {
                return Optional.empty();
            }
            require2xx(response);
            return Optional.of(mapper.readValue(response.body(), ProtectionPlan.class));
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao obter plano do device", e);
        }
    }

    public ProtectionPlan upsertDevicePlan(UUID deviceId, ProtectionPlan.PlanType planType, List<String> sources) {
        try {
            String body = mapper.writeValueAsString(Map.of("planType", planType, "sources", sources));
            HttpResponse<String> response = sendJson("/api/devices/" + deviceId + "/plan", body, "PUT");
            return mapper.readValue(response.body(), ProtectionPlan.class);
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao salvar plano do device", e);
        }
    }

    public UUID startSnapshot(UUID deviceId, String sourcePath) {
        try {
            String body = mapper.writeValueAsString(Map.of("deviceId", deviceId.toString(), "sourcePath", sourcePath));
            HttpResponse<String> response = sendJson("/api/snapshots/start", body);
            Map<String, Object> json = mapper.readValue(response.body(), new TypeReference<>() {});
            return UUID.fromString((String) json.get("id"));
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao iniciar snapshot", e);
        }
    }

    public Set<String> checkChunks(List<String> hashes) {
        try {
            String body = mapper.writeValueAsString(Map.of("hashes", hashes));
            HttpResponse<String> response = sendJson("/api/chunks/check", body);
            Map<String, List<String>> json = mapper.readValue(response.body(), new TypeReference<>() {});
            return new HashSet<>(json.getOrDefault("existing", List.of()));
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao verificar chunks", e);
        }
    }

    public void uploadChunk(ChunkPayload chunk) {
        try {
            String boundary = "----keeply-" + UUID.randomUUID();
            byte[] body = multipart(boundary, chunk);
            HttpRequest request = authorized("/api/chunks/upload?hash=%s&originalSize=%d&compressedSize=%d"
                    .formatted(chunk.hash(), chunk.originalSize(), chunk.compressedSize()))
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                    .build();

            HttpResponse<String> response = sendWithRefreshRetry(request);
            require2xx(response);
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao enviar chunk " + chunk.hash(), e);
        }
    }

    public void completeSnapshot(UUID snapshotId, String manifestJson, long totalFiles, long totalOriginalSize, long totalCompressedSize) {
        try {
            String body = mapper.writeValueAsString(Map.of(
                    "manifestJson", manifestJson,
                    "totalFiles", totalFiles,
                    "totalOriginalSize", totalOriginalSize,
                    "totalCompressedSize", totalCompressedSize
            ));
            sendJson("/api/snapshots/" + snapshotId + "/complete", body);
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao concluir snapshot", e);
        }
    }

    public void failSnapshot(UUID snapshotId, String errorMessage) {
        try {
            String body = mapper.writeValueAsString(Map.of("errorMessage", errorMessage));
            sendJson("/api/snapshots/" + snapshotId + "/fail", body);
        } catch (Exception ignored) {
        }
    }

    public List<SnapshotSummary> listSnapshots() {
        try {
            HttpRequest request = authorized("/api/snapshots").GET().build();
            HttpResponse<String> response = sendWithRefreshRetry(request);
            require2xx(response);
            return mapper.readValue(response.body(), new TypeReference<>() {});
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao listar snapshots", e);
        }
    }

    public String downloadManifest(UUID snapshotId) {
        try {
            HttpRequest request = authorized("/api/snapshots/" + snapshotId + "/manifest").GET().build();
            HttpResponse<String> response = sendWithRefreshRetry(request);
            require2xx(response);
            return response.body();
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao baixar manifesto", e);
        }
    }

    public byte[] downloadChunk(String hash) {
        try {
            HttpRequest request = authorized("/api/chunks/" + hash + "/download").GET().build();
            HttpResponse<byte[]> response = sendBytesWithRefreshRetry(request);
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("HTTP " + response.statusCode());
            }
            return response.body();
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao baixar chunk " + hash, e);
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

    private HttpResponse<String> sendJson(String path, String body) throws Exception {
        return sendJson(path, body, "POST");
    }

    private HttpResponse<String> sendJson(String path, String body, String method) throws Exception {
        HttpRequest request = authorized(path)
                .header("Content-Type", "application/json")
                .method(method, HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> response = sendWithRefreshRetry(request);
        require2xx(response);
        return response;
    }

    private HttpRequest.Builder authorized(String path) {
        HttpRequest.Builder builder = HttpRequest.newBuilder().uri(URI.create(baseUrl + path));
        if (session != null && !blank(session.accessToken())) {
            builder.header("Authorization", "Bearer " + session.accessToken());
        }
        return builder;
    }

    private HttpResponse<String> sendWithRefreshRetry(HttpRequest request) throws Exception {
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 401 && session != null && !blank(session.refreshToken())) {
            refreshSession();
            HttpRequest retry = retryWithUpdatedAccessToken(request);
            response = http.send(retry, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 401) {
                throw new IllegalStateException("Sessão expirada ou revogada. Faça login novamente.");
            }
        }
        return response;
    }

    private HttpResponse<byte[]> sendBytesWithRefreshRetry(HttpRequest request) throws Exception {
        HttpResponse<byte[]> response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() == 401 && session != null && !blank(session.refreshToken())) {
            refreshSession();
            HttpRequest retry = retryWithUpdatedAccessToken(request);
            response = http.send(retry, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() == 401) {
                throw new IllegalStateException("Sessão expirada ou revogada. Faça login novamente.");
            }
        }
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

    private static byte[] multipart(String boundary, ChunkPayload chunk) {
        String header = "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"file\"; filename=\"" + chunk.hash() + ".gz\"\r\n"
                + "Content-Type: application/gzip\r\n\r\n";
        String footer = "\r\n--" + boundary + "--\r\n";

        byte[] h = header.getBytes(StandardCharsets.UTF_8);
        byte[] f = footer.getBytes(StandardCharsets.UTF_8);
        byte[] data = chunk.compressedBytes();

        byte[] out = new byte[h.length + data.length + f.length];
        System.arraycopy(h, 0, out, 0, h.length);
        System.arraycopy(data, 0, out, h.length, data.length);
        System.arraycopy(f, 0, out, h.length + data.length, f.length);
        return out;
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
