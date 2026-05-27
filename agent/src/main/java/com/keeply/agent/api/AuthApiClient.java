package com.keeply.agent.api;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.keeply.agent.model.DeviceSession;

import java.net.http.HttpResponse;
import java.util.Map;
import java.util.UUID;

public final class AuthApiClient {
    private final HttpExecutor executor;
    private final ObjectMapper mapper;

    AuthApiClient(HttpExecutor executor, ObjectMapper mapper) {
        this.executor = executor;
        this.mapper = mapper;
    }

    DeviceSession loginDevice(String email, String password, String installationId, String hostname,
                              String osName, String agentVersion, String traceId) throws Exception {
        String body = mapper.writeValueAsString(Map.of(
                "email", email,
                "password", password,
                "deviceInstallationId", installationId,
                "hostname", hostname,
                "osName", osName,
                "agentVersion", agentVersion));
        HttpResponse<String> response = executor.sendPublicJson("/api/auth/login-device", body, traceId);
        return parseSession(response.body(), installationId);
    }

    DeviceSession refresh(DeviceSession session, String traceId) throws Exception {
        String body = mapper.writeValueAsString(Map.of(
                "refreshToken", session.refreshToken(),
                "deviceInstallationId", session.deviceInstallationId()));
        HttpResponse<String> response = executor.sendPublicJson("/api/auth/refresh", body, traceId);
        return parseSession(response.body(), session.deviceInstallationId());
    }

    private DeviceSession parseSession(String body, String installationId) throws Exception {
        Map<String, Object> json = mapper.readValue(body, new TypeReference<>() {});
        UUID userId = json.get("userId") == null ? null : UUID.fromString((String) json.get("userId"));
        UUID deviceId = json.get("deviceId") == null ? null : UUID.fromString((String) json.get("deviceId"));
        return new DeviceSession(installationId, deviceId, (String) json.get("accessToken"),
                (String) json.get("refreshToken"), userId, (String) json.get("email"));
    }
}
