package com.keeply.agent.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.keeply.agent.model.ProtectionPlan;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class AgentConfigWriter {
    private final Path path;
    private final ObjectMapper yaml = new ObjectMapper(new YAMLFactory());

    public AgentConfigWriter(Path path) {
        this.path = path;
    }

    public void saveSchedule(String backendUrl, String email, List<String> sources, String cron) throws Exception {
        if (backendUrl == null || backendUrl.isBlank()) {
            throw new IllegalStateException("Backend URL é obrigatório.");
        }
        Map<String, Object> root = readRoot();
        root.put("backend", Map.of("url", backendUrl));

        Map<String, Object> auth = section(root, "auth");
        if (email != null && !email.isBlank()) auth.put("email", email);
        auth.remove("password"); // nunca persistir senha em YAML
        root.put("auth", auth);

        Map<String, Object> backup = section(root, "backup");
        backup.put("sources", sources == null ? List.of() : sources);
        root.put("backup", backup);

        Map<String, Object> schedule = section(root, "schedule");
        schedule.put("cron", cron);
        root.put("schedule", schedule);
        write(root);
    }

    public void savePlan(String backendUrl, String email, ProtectionPlan plan) throws Exception {
        Map<String, Object> root = readRoot();
        Map<String, Object> backend = section(root, "backend");
        backend.put("url", backendUrl);
        root.put("backend", backend);

        Map<String, Object> auth = section(root, "auth");
        if (email != null && !email.isBlank()) auth.put("email", email);
        auth.remove("password"); // nunca persistir senha em YAML
        root.put("auth", auth);

        Map<String, Object> backup = section(root, "backup");
        backup.put("sources", plan.sources());
        root.put("backup", backup);

        Map<String, Object> schedule = section(root, "schedule");
        schedule.put("cron", plan.scheduleCron());
        root.put("schedule", schedule);

        Map<String, Object> retention = section(root, "retention");
        retention.put("mode", plan.retentionMode() != null ? plan.retentionMode().name() : ProtectionPlan.RetentionMode.KEEP_ALL.name());
        if (plan.retentionMode() == ProtectionPlan.RetentionMode.KEEP_DAYS && plan.retentionDays() != null) {
            retention.put("days", plan.retentionDays());
        } else {
            retention.remove("days");
        }
        root.put("retention", retention);
        write(root);
    }

    public void saveEncryptionEnabled(boolean enabled) throws Exception {
        Map<String, Object> root = readRoot();
        Map<String, Object> enc = section(root, "encryption");
        enc.put("enabled", enabled);
        if (!enabled) enc.remove("password");
        root.put("encryption", enc);
        write(root);
    }

    public void saveEncryptionPassword(String password) throws Exception {
        Map<String, Object> root = readRoot();
        Map<String, Object> enc = section(root, "encryption");
        enc.put("enabled", true);
        // A senha de criptografia NÃO deve ser salva em texto plano no YAML.
        // Removendo explicitamente caso existisse.
        enc.remove("password");
        root.put("encryption", enc);
        write(root);
    }

    public Path path() {
        return path;
    }

    private Map<String, Object> readRoot() throws Exception {
        if (!Files.exists(path)) {
            return new LinkedHashMap<>();
        }
        Map<String, Object> root = yaml.readValue(Files.readString(path),
                new TypeReference<LinkedHashMap<String, Object>>() {});
        return root == null ? new LinkedHashMap<>() : root;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> section(Map<String, Object> root, String name) {
        return root.get(name) instanceof Map<?, ?> existing
                ? new LinkedHashMap<>((Map<String, Object>) existing)
                : new LinkedHashMap<>();
    }

    private void write(Map<String, Object> root) throws Exception {
        Files.createDirectories(path.getParent());
        yaml.writeValue(path.toFile(), root);
    }
}
