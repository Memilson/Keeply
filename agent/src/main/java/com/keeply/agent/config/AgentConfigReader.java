package com.keeply.agent.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.keeply.agent.model.LocalPlanState;
import com.keeply.agent.model.ProtectionPlan;

import java.time.Instant;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class AgentConfigReader {
    private final Path path;
    private final ObjectMapper yaml = new ObjectMapper(new YAMLFactory());

    public AgentConfigReader(Path path) {
        this.path = path;
    }

    public Optional<UiConfig> read() throws Exception {
        if (!Files.exists(path)) {
            return Optional.empty();
        }
        Map<String, Object> root = readRoot();
        if (root == null) {
            return Optional.of(new UiConfig(null, null, List.of(), null, false, false, false, null, null, null, null, null));
        }
        return Optional.of(new UiConfig(
                property(root, "backend", "url"),
                property(root, "auth", "email"),
                sources(root),
                property(root, "schedule", "cron"),
                boolProperty(root, "cdp", "enabled"),
                boolProperty(root, "validation", "enabled"),
                boolProperty(root, "encryption", "enabled"),
                property(root, "encryption", "password"),
                property(root, "retention", "mode"),
                intProperty(root, "retention", "days"),
                instantProperty(root, "planSync", "localUpdatedAt"),
                instantProperty(root, "planSync", "lastRemoteUpdatedAt")));
    }

    public Optional<LocalPlanState> readLocalPlanState() throws Exception {
        Optional<UiConfig> uiConfig = read();
        if (uiConfig.isEmpty()) {
            return Optional.empty();
        }
        UiConfig config = uiConfig.get();
        if (config.sources().isEmpty()
                && config.cron() == null
                && !config.cdpEnabled()
                && !config.validationEnabled()
                && !config.encryptionEnabled()
                && config.retentionMode() == null
                && config.retentionDays() == null) {
            return Optional.empty();
        }
        ProtectionPlan.RetentionMode retentionMode = parseRetentionMode(config.retentionMode());
        ProtectionPlan.PlanType planType = config.sources().size() == 1
                ? ProtectionPlan.PlanType.DEFAULT
                : ProtectionPlan.PlanType.CUSTOM;
        ProtectionPlan plan = new ProtectionPlan(
                planType,
                config.sources(),
                config.cdpEnabled(),
                config.validationEnabled(),
                config.encryptionEnabled(),
                config.cron(),
                retentionMode,
                retentionMode == ProtectionPlan.RetentionMode.KEEP_DAYS ? config.retentionDays() : null,
                config.lastRemoteUpdatedAt());
        return Optional.of(new LocalPlanState(
                plan,
                config.localUpdatedAt(),
                config.lastRemoteUpdatedAt(),
                config.encryptionPassword()));
    }

    public Path path() {
        return path;
    }

    private Map<String, Object> readRoot() throws Exception {
        Map<String, Object> root = yaml.readValue(Files.readString(path),
                new TypeReference<LinkedHashMap<String, Object>>() {});
        return root == null ? new LinkedHashMap<>() : root;
    }

    private static String property(Map<String, Object> root, String section, String key) {
        if (root.get(section) instanceof Map<?, ?> values && values.get(key) != null) {
            return values.get(key).toString();
        }
        return null;
    }

    private static List<String> sources(Map<String, Object> root) {
        if (root.get("backup") instanceof Map<?, ?> backup && backup.get("sources") instanceof List<?> values) {
            return values.stream().map(String::valueOf).toList();
        }
        return List.of();
    }

    private static boolean boolProperty(Map<String, Object> root, String section, String key) {
        if (root.get(section) instanceof Map<?, ?> values && values.get(key) instanceof Boolean b) {
            return b;
        }
        return false;
    }

    private static Integer intProperty(Map<String, Object> root, String section, String key) {
        if (root.get(section) instanceof Map<?, ?> values && values.get(key) instanceof Number number) {
            return number.intValue();
        }
        return null;
    }

    private static Instant instantProperty(Map<String, Object> root, String section, String key) {
        String value = property(root, section, key);
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static ProtectionPlan.RetentionMode parseRetentionMode(String value) {
        if (value == null || value.isBlank()) {
            return ProtectionPlan.RetentionMode.KEEP_ALL;
        }
        try {
            return ProtectionPlan.RetentionMode.valueOf(value);
        } catch (IllegalArgumentException ignored) {
            return ProtectionPlan.RetentionMode.KEEP_ALL;
        }
    }

    public record UiConfig(
            String backendUrl,
            String email,
            List<String> sources,
            String cron,
            boolean cdpEnabled,
            boolean validationEnabled,
            boolean encryptionEnabled,
            String encryptionPassword,
            String retentionMode,
            Integer retentionDays,
            Instant localUpdatedAt,
            Instant lastRemoteUpdatedAt
    ) {
    }
}
