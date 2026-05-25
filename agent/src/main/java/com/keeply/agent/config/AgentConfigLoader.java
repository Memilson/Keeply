package com.keeply.agent.config;

import com.cronutils.model.CronType;
import com.cronutils.model.definition.CronDefinitionBuilder;
import com.cronutils.parser.CronParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class AgentConfigLoader {
    private final CronParser cronParser = new CronParser(CronDefinitionBuilder.instanceDefinitionFor(CronType.UNIX));
    private final ObjectMapper mapper = new ObjectMapper(new YAMLFactory())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);

    public AgentConfig load(Path path) {
        if (!Files.exists(path)) {
            throw new IllegalArgumentException("Arquivo de configuração não encontrado: " + path);
        }

        try {
            AgentConfig config = mapper.readValue(path.toFile(), AgentConfig.class);
            return validate(config, path);
        } catch (IOException e) {
            throw new IllegalArgumentException("Falha ao ler YAML do agente: " + path, e);
        }
    }

    private AgentConfig validate(AgentConfig config, Path path) {
        if (config == null) {
            throw new IllegalArgumentException("Configuração vazia em " + path);
        }

        List<String> errors = new ArrayList<>();

        if (config.backend() == null || isBlank(config.backend().url())) {
            errors.add("backend.url é obrigatório");
        }

        if (config.backup() == null || config.backup().sources() == null || config.backup().sources().isEmpty()) {
            errors.add("backup.sources deve conter pelo menos uma pasta");
        } else {
            for (Path source : config.backup().sources()) {
                if (source == null) {
                    errors.add("backup.sources não pode conter valores nulos");
                    continue;
                }
                Path normalized = source.toAbsolutePath().normalize();
                if (!Files.exists(normalized)) {
                    errors.add("backup.sources contém caminho inexistente: " + normalized);
                } else if (!Files.isDirectory(normalized)) {
                    errors.add("backup.sources deve conter apenas diretórios: " + normalized);
                }
            }
        }

        if (config.schedule() == null || isBlank(config.schedule().cron())) {
            errors.add("schedule.cron é obrigatório");
        } else {
            try {
                cronParser.parse(config.schedule().cron()).validate();
            } catch (Exception e) {
                errors.add("schedule.cron inválido: " + config.schedule().cron());
            }
        }

        if (!errors.isEmpty()) {
            throw new IllegalArgumentException("Configuração inválida: " + String.join("; ", errors));
        }

        return config;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
