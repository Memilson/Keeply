package com.keeply.agent.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class AgentConfigLoaderTest {
    @TempDir
    Path tempDir;

    @Test
    void loadValidYaml() throws Exception {
        Path source = Files.createDirectory(tempDir.resolve("source"));
        Path config = tempDir.resolve("agent.yaml");
        Files.writeString(config, """
                backend:
                  url: http://localhost:8080
                auth:
                  email: keeply@keeply.com
                  password: keeply123
                backup:
                  sources:
                    - %s
                schedule:
                  cron: \"*/5 * * * *\"
                """.formatted(source.toAbsolutePath()));

        AgentConfig loaded = new AgentConfigLoader().load(config);

        assertEquals("http://localhost:8080", loaded.backend().url());
        assertEquals(1, loaded.backup().sources().size());
        assertEquals("*/5 * * * *", loaded.schedule().cron());
    }

    @Test
    void rejectInvalidCron() throws Exception {
        Path source = Files.createDirectory(tempDir.resolve("source"));
        Path config = tempDir.resolve("agent.yaml");
        Files.writeString(config, """
                backend:
                  url: http://localhost:8080
                auth:
                  email: keeply@keeply.com
                  password: keeply123
                backup:
                  sources:
                    - %s
                schedule:
                  cron: \"invalid\"
                """.formatted(source.toAbsolutePath()));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> new AgentConfigLoader().load(config));

        assertTrue(ex.getMessage().contains("schedule.cron inválido"));
    }

    @Test
    void rejectMissingSourcePath() throws Exception {
        Path config = tempDir.resolve("agent.yaml");
        Files.writeString(config, """
                backend:
                  url: http://localhost:8080
                auth:
                  email: keeply@keeply.com
                  password: keeply123
                backup:
                  sources:
                    - /path/that/does/not/exist
                schedule:
                  cron: \"*/5 * * * *\"
                """);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> new AgentConfigLoader().load(config));

        assertTrue(ex.getMessage().contains("caminho inexistente"));
    }
}
