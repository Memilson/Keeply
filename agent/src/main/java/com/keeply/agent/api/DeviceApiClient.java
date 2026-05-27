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
        String body = mapper.writeValueAsString(Map.of("planType", type, "sources", sources));
        return mapper.readValue(executor.sendJson("/api/devices/" + deviceId + "/plan",
                body, "PUT", traceId).body(), ProtectionPlan.class);
    }
}
