package com.keeply.agent.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

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
        Map<String, Object> root = yaml.readValue(Files.readString(path),
                new TypeReference<LinkedHashMap<String, Object>>() {});
        if (root == null) {
            return Optional.of(new UiConfig(null, null, List.of(), null, false, false, null, null, null));
        }
        return Optional.of(new UiConfig(
                property(root, "backend", "url"),
                property(root, "auth", "email"),
                sources(root),
                property(root, "schedule", "cron"),
                boolProperty(root, "validation", "enabled"),
                boolProperty(root, "encryption", "enabled"),
                property(root, "encryption", "password"),
                property(root, "retention", "mode"),
                intProperty(root, "retention", "days")));
    }

    public Path path() {
        return path;
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

    public record UiConfig(
            String backendUrl,
            String email,
            List<String> sources,
            String cron,
            boolean validationEnabled,
            boolean encryptionEnabled,
            String encryptionPassword,
            String retentionMode,
            Integer retentionDays
    ) {
    }
}
