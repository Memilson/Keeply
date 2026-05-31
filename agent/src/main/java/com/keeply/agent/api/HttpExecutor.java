package com.keeply.agent.api;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.keeply.agent.model.DeviceSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.function.Supplier;

public final class HttpExecutor {
    private static final Logger log = LoggerFactory.getLogger(HttpExecutor.class);
    private static final String TRACE_ID_HEADER = "X-Trace-Id";

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();
    private final ObjectMapper mapper;
    private final String baseUrl;
    private final Supplier<DeviceSession> sessionSupplier;
    private final Runnable refreshSession;

    HttpExecutor(String baseUrl, ObjectMapper mapper, Supplier<DeviceSession> sessionSupplier,
                 Runnable refreshSession) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.mapper = mapper;
        this.sessionSupplier = sessionSupplier;
        this.refreshSession = refreshSession;
    }

    HttpResponse<String> sendPublicJson(String path, String body, String traceId) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .header(TRACE_ID_HEADER, traceId)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> response = sendAndLog(request, traceId);
        require2xx(response);
        return response;
    }

    HttpResponse<String> sendJson(String path, String body, String method, String traceId) throws Exception {
        HttpRequest request = authorized(path, traceId)
                .header("Content-Type", "application/json")
                .method(method, HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> response = sendWithRefreshRetry(request, traceId);
        require2xx(response);
        return response;
    }

    HttpResponse<String> get(String path, String traceId) throws Exception {
        HttpResponse<String> response = sendWithRefreshRetry(authorized(path, traceId).GET().build(), traceId);
        require2xx(response);
        return response;
    }

    HttpResponse<String> getAllowingNotFound(String path, String traceId) throws Exception {
        return sendWithRefreshRetry(authorized(path, traceId).GET().build(), traceId);
    }

    void require2xx(HttpResponse<String> response) {
        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            return;
        }
        String message = "HTTP " + response.statusCode();
        String error = null;
        try {
            Map<String, Object> errorMap = mapper.readValue(response.body(), new TypeReference<>() {});
            if (errorMap.containsKey("error")) {
                error = String.valueOf(errorMap.get("error"));
                message = error;
            }
        } catch (Exception ignored) {
            if (response.body() != null && !response.body().isBlank()) {
                message += ": " + response.body();
            }
        }
        throw new ApiException(response.statusCode(), message, error);
    }

    private HttpRequest.Builder authorized(String path, String traceId) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .header(TRACE_ID_HEADER, traceId)
                .timeout(Duration.ofSeconds(60));
        DeviceSession session = sessionSupplier.get();
        if (session != null && !blank(session.accessToken())) {
            builder.header("Authorization", "Bearer " + session.accessToken());
        }
        return builder;
    }

    private HttpResponse<String> sendWithRefreshRetry(HttpRequest request, String traceId) throws Exception {
        HttpResponse<String> response = sendAndLog(request, traceId);
        DeviceSession session = sessionSupplier.get();
        if (response.statusCode() == 401 && session != null && !blank(session.refreshToken())) {
            log.info("[{}] Recebido 401, tentando refresh session", traceId);
            refreshSession.run();
            response = sendAndLog(retryWithUpdatedAccessToken(request), traceId);
            if (response.statusCode() == 401) {
                throw new IllegalStateException("Sessão expirada ou revogada. Faça login novamente.");
            }
        }
        return response;
    }

    private HttpResponse<String> sendAndLog(HttpRequest request, String traceId) throws Exception {
        log.debug("[{}] Request: {} {}", traceId, request.method(), request.uri());
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        log.debug("[{}] Response: {} ({} bytes)", traceId, response.statusCode(), response.body().length());
        return response;
    }

    private HttpRequest retryWithUpdatedAccessToken(HttpRequest original) {
        HttpRequest.Builder builder = HttpRequest.newBuilder().uri(original.uri());
        original.headers().map().forEach((header, values) -> {
            if (!"authorization".equalsIgnoreCase(header)) {
                values.forEach(value -> builder.header(header, value));
            }
        });
        DeviceSession session = sessionSupplier.get();
        if (session != null && !blank(session.accessToken())) {
            builder.header("Authorization", "Bearer " + session.accessToken());
        }
        return builder.method(original.method(),
                original.bodyPublisher().orElse(HttpRequest.BodyPublishers.noBody())).build();
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
