package com.keeply.agent.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.keeply.agent.model.ProtectionPlan;

import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class DeviceApiClient {
    private final HttpExecutor executor;
    private final ObjectMapper mapper;

    DeviceApiClient(HttpExecutor executor, ObjectMapper mapper) {
        this.executor = executor;
        this.mapper = mapper;
    }

    void heartbeat(UUID deviceId, String traceId) throws Exception {
        executor.require2xx(executor.sendJson(ApiEndpoints.deviceHeartbeat(deviceId), "", "PATCH", traceId));
    }

    Optional<ProtectionPlan> getPlan(UUID deviceId, String traceId) throws Exception {
        HttpResponse<String> response = executor.getAllowingNotFound(ApiEndpoints.devicePlan(deviceId), traceId);
        if (response.statusCode() == 404) {
            return Optional.empty();
        }
        executor.require2xx(response);
        return Optional.of(mapper.readValue(response.body(), ProtectionPlan.class));
    }

    ProtectionPlan upsertPlan(UUID deviceId, ProtectionPlan.PlanType type, List<String> sources,
                              String traceId) throws Exception {
        return upsertPlan(deviceId, type, sources, false, false, false, null, traceId);
    }

    ProtectionPlan upsertPlan(UUID deviceId, ProtectionPlan.PlanType type, List<String> sources,
                              boolean cdpEnabled, boolean validationEnabled,
                              boolean encryptionEnabled, String scheduleCron,
                              String traceId) throws Exception {
        return upsertPlan(deviceId, type, sources, cdpEnabled, validationEnabled, encryptionEnabled, scheduleCron,
                null, null, null, traceId);
    }

    ProtectionPlan upsertPlan(UUID deviceId, ProtectionPlan.PlanType type, List<String> sources,
                              boolean cdpEnabled, boolean validationEnabled,
                              boolean encryptionEnabled, String scheduleCron,
                              String encryptionPassword, ProtectionPlan.RetentionMode retentionMode,
                              Integer retentionDays, String traceId) throws Exception {
        Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("planType", type);
        payload.put("sources", sources);
        payload.put("cdpEnabled", cdpEnabled);
        payload.put("validationEnabled", validationEnabled);
        payload.put("encryptionEnabled", encryptionEnabled);
        if (scheduleCron != null) payload.put("scheduleCron", scheduleCron);
        if (encryptionPassword != null && !encryptionPassword.isBlank()) payload.put("encryptionPassword", encryptionPassword);
        if (retentionMode != null) payload.put("retentionMode", retentionMode);
        if (retentionDays != null) payload.put("retentionDays", retentionDays);
        String body = mapper.writeValueAsString(payload);
        return mapper.readValue(executor.sendJson(ApiEndpoints.devicePlan(deviceId),
                body, "PUT", traceId).body(), ProtectionPlan.class);
    }
}
