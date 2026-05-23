package com.keeply.agent.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.keeply.agent.model.ProtectionPlan;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class PlanConfigSync {
    private final ObjectMapper yaml = new ObjectMapper(new YAMLFactory());

    public void applyPlan(Path configPath, ProtectionPlan plan) {
        try {
            Files.createDirectories(configPath.toAbsolutePath().getParent());
            Map<String, Object> root = Files.exists(configPath)
                    ? yaml.readValue(Files.readString(configPath), new TypeReference<>() {})
                    : new LinkedHashMap<>();
            if (root == null) {
                root = new LinkedHashMap<>();
            }

            Map<String, Object> backup = root.get("backup") instanceof Map<?, ?> existing
                    ? new LinkedHashMap<>((Map<String, Object>) existing)
                    : new LinkedHashMap<>();
            backup.put("sources", new ArrayList<>(plan.sources()));
            root.put("backup", backup);

            yaml.writeValue(configPath.toFile(), root);
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao sincronizar plano no agent.yaml", e);
        }
    }

    public List<String> readSources(Path configPath) {
        try {
            if (!Files.exists(configPath)) {
                return List.of();
            }
            Map<String, Object> root = yaml.readValue(Files.readString(configPath), new TypeReference<>() {});
            if (root == null || !(root.get("backup") instanceof Map<?, ?> backup) || !(backup.get("sources") instanceof List<?> list)) {
                return List.of();
            }
            return list.stream().map(String::valueOf).toList();
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao ler sources do agent.yaml", e);
        }
    }
}
