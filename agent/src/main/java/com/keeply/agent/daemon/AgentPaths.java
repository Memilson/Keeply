package com.keeply.agent.daemon;

import java.nio.file.Path;
import java.nio.file.Paths;

public final class AgentPaths {
    private AgentPaths() {
    }

    public static Path resolveDataDir() {
        return Paths.get("/home/angelo/keeply")
                .toAbsolutePath()
                .normalize();
    }

    public static Path resolveDefaultConfigPath() {
        return resolveDataDir().resolve("agent.yaml")
                .toAbsolutePath()
                .normalize();
    }

    public static Path resolveDeviceAuthPath() {
        return resolveDataDir().resolve("device-auth.json")
                .toAbsolutePath()
                .normalize();
    }
}
