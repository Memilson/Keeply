package com.keeply.agent.auth;

import com.keeply.agent.daemon.AgentPaths;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

public final class DeviceIdentity {
    private DeviceIdentity() {
    }

    public static String getOrCreate() {
        Path path = AgentPaths.resolveDeviceIdPath();
        try {
            if (Files.exists(path)) {
                String id = Files.readString(path).trim();
                if (!id.isBlank()) {
                    return id;
                }
            }
            String newId = UUID.randomUUID().toString();
            Files.createDirectories(path.getParent());
            Files.writeString(path, newId);
            return newId;
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao gerenciar identidade persistente do dispositivo", e);
        }
    }
}
