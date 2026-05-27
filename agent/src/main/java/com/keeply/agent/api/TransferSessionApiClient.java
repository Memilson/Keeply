package com.keeply.agent.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.keeply.agent.model.TransferCredentials;

public final class TransferSessionApiClient {
    private final HttpExecutor executor;
    private final ObjectMapper mapper;

    TransferSessionApiClient(HttpExecutor executor, ObjectMapper mapper) {
        this.executor = executor;
        this.mapper = mapper;
    }

    TransferCredentials create(String path, String traceId) throws Exception {
        return mapper.readValue(executor.sendJson(path, "{}", "POST", traceId).body(),
                TransferCredentials.class);
    }

    void finish(String path, String traceId) throws Exception {
        executor.sendJson(path, "{}", "POST", traceId);
    }
}
