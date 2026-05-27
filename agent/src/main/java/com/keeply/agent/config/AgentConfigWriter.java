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

public final class AgentConfigWriter {
    private final Path path;
    private final ObjectMapper yaml = new ObjectMapper(new YAMLFactory());

    public AgentConfigWriter(Path path) {
        this.path = path;
    }

    public void saveSchedule(String backendUrl, String email, String password, List<String> sources, String cron) throws Exception {
        if (backendUrl == null || backendUrl.isBlank()) {
            throw new IllegalStateException("Backend URL é obrigatório.");
        }
        if (sources == null || sources.isEmpty()) {
            throw new IllegalStateException("Informe pelo menos uma pasta em 'Pastas de backup'.");
        }
        Map<String, Object> root = readRoot();
        root.put("backend", Map.of("url", backendUrl));

        Map<String, Object> auth = section(root, "auth");
        if (email != null && !email.isBlank()) {
            auth.put("email", email);
        }
        if (password != null && !password.isBlank()) {
            auth.put("password", password);
        }
        root.put("auth", auth);

        root.put("backup", Map.of("sources", new ArrayList<>(sources)));
        Map<String, Object> schedule = section(root, "schedule");
        schedule.put("cron", cron);
        root.put("schedule", schedule);
        write(root);
    }

    public void savePlan(String backendUrl, String email, String password, ProtectionPlan plan) throws Exception {
        Map<String, Object> root = readRoot();
        Map<String, Object> backend = section(root, "backend");
        backend.put("url", backendUrl);
        root.put("backend", backend);

        Map<String, Object> auth = section(root, "auth");
        if (email != null && !email.isBlank()) {
            auth.put("email", email);
        }
        if (password != null && !password.isBlank()) {
            auth.put("password", password);
        }
        root.put("auth", auth);

        Map<String, Object> backup = section(root, "backup");
        backup.put("sources", new ArrayList<>(plan.sources()));
        root.put("backup", backup);
        if (!(root.get("schedule") instanceof Map<?, ?>)) {
            root.put("schedule", Map.of("cron", "0 2 * * *", "runOnStartup", false));
        }
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
