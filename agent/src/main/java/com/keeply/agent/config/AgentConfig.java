package com.keeply.agent.config;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

public record AgentConfig(
        Backend backend,
        Auth auth,
        Device device,
        Backup backup,
        Schedule schedule,
        Retention retention,
        Validation validation,
        Cdp cdp,
        Encryption encryption,
        PlanSync planSync
) {
    public record Backend(String url) {
    }

    public record Auth(String email) {
    }

    public record Device(String name) {
    }

    public record Backup(List<Path> sources) {
    }

    public record Schedule(String cron, Boolean runOnStartup) {
    }

    public record Retention(String mode, Integer days) {
    }

    public record Validation(Boolean enabled) {
    }

    public record Cdp(Boolean enabled) {
    }

    public record Encryption(Boolean enabled, String password) {
    }

    public record PlanSync(Instant localUpdatedAt, Instant lastRemoteUpdatedAt) {
    }
}
