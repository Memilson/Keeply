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

    Optional<ProtectionPlan> getPlan(UUID deviceId, String traceId) throws Exception {
        HttpResponse<String> response = executor.getAllowingNotFound("/api/devices/" + deviceId + "/plan", traceId);
        if (response.statusCode() == 404) {
            return Optional.empty();
        }
        executor.require2xx(response);
        return Optional.of(mapper.readValue(response.body(), ProtectionPlan.class));
    }

    ProtectionPlan upsertPlan(UUID deviceId, ProtectionPlan.PlanType type, List<String> sources,
                              String traceId) throws Exception {
        return upsertPlan(deviceId, type, sources, false, false, null, traceId);
    }

    ProtectionPlan upsertPlan(UUID deviceId, ProtectionPlan.PlanType type, List<String> sources,
                              boolean cdpEnabled, boolean encryptionEnabled, String scheduleCron,
                              String traceId) throws Exception {
        return upsertPlan(deviceId, type, sources, cdpEnabled, encryptionEnabled, scheduleCron,
                null, null, null, traceId);
    }

    ProtectionPlan upsertPlan(UUID deviceId, ProtectionPlan.PlanType type, List<String> sources,
                              boolean cdpEnabled, boolean encryptionEnabled, String scheduleCron,
                              String encryptionPassword, ProtectionPlan.RetentionMode retentionMode,
                              Integer retentionDays, String traceId) throws Exception {
        Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("planType", type);
        payload.put("sources", sources);
        payload.put("cdpEnabled", cdpEnabled);
        payload.put("encryptionEnabled", encryptionEnabled);
        if (scheduleCron != null) payload.put("scheduleCron", scheduleCron);
        if (encryptionPassword != null && !encryptionPassword.isBlank()) payload.put("encryptionPassword", encryptionPassword);
        if (retentionMode != null) payload.put("retentionMode", retentionMode);
        if (retentionDays != null) payload.put("retentionDays", retentionDays);
        String body = mapper.writeValueAsString(payload);
        return mapper.readValue(executor.sendJson("/api/devices/" + deviceId + "/plan",
                body, "PUT", traceId).body(), ProtectionPlan.class);
    }
}
