package com.keeply.agent.api;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.keeply.agent.model.ChunkPayload;
import com.keeply.agent.model.SnapshotSummary;

import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class BackendClient {
    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .findAndRegisterModules();
    private final String baseUrl;
    private String token;

    public BackendClient(String baseUrl) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String login(String email, String password) {
        try {
            String body = mapper.writeValueAsString(Map.of("email", email, "password", password));
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/auth/login"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            require2xx(response);
            Map<String, Object> json = mapper.readValue(response.body(), new TypeReference<>() {});
            token = (String) json.get("accessToken");
            return token;
        } catch (Exception e) {
            throw new IllegalStateException("Falha no login", e);
        }
    }

    public UUID registerDevice(String name, String hostname, String os, String version) {
        try {
            String body = mapper.writeValueAsString(Map.of(
                    "name", name,
                    "hostname", hostname,
                    "os", os,
                    "agentVersion", version
            ));

            HttpResponse<String> response = sendJson("/api/devices/register", body);
            Map<String, Object> json = mapper.readValue(response.body(), new TypeReference<>() {});
            return UUID.fromString((String) json.get("id"));
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao registrar device", e);
        }
    }

    public UUID startSnapshot(UUID deviceId, String sourcePath) {
        try {
            String body = mapper.writeValueAsString(Map.of(
                    "deviceId", deviceId.toString(),
                    "sourcePath", sourcePath
            ));
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

            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
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
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            require2xx(response);
            return mapper.readValue(response.body(), new TypeReference<>() {});
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao listar snapshots", e);
        }
    }

    public String downloadManifest(UUID snapshotId) {
        try {
            HttpRequest request = authorized("/api/snapshots/" + snapshotId + "/manifest").GET().build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            require2xx(response);
            return response.body();
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao baixar manifesto", e);
        }
    }

    public byte[] downloadChunk(String hash) {
        try {
            HttpRequest request = authorized("/api/chunks/" + hash + "/download").GET().build();
            HttpResponse<byte[]> response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("HTTP " + response.statusCode());
            }
            return response.body();
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao baixar chunk " + hash, e);
        }
    }

    private HttpResponse<String> sendJson(String path, String body) throws Exception {
        HttpRequest request = authorized(path)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        require2xx(response);
        return response;
    }

    private HttpRequest.Builder authorized(String path) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path));
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        return builder;
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
}
