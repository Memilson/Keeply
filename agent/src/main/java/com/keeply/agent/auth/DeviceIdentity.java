package com.keeply.agent.auth;

import com.keeply.agent.model.DeviceSession;

import java.util.UUID;

public final class DeviceIdentity {
    private DeviceIdentity() {
    }

    public static String getOrCreate(DeviceAuthStore store) {
        return store.load()
                .map(DeviceSession::deviceInstallationId)
                .filter(v -> v != null && !v.isBlank())
                .orElseGet(() -> UUID.randomUUID().toString());
    }
}
