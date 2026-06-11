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
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

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

        if (config.schedule() != null && !isBlank(config.schedule().cron())) {
            try {
                cronParser.parse(config.schedule().cron()).validate();
            } catch (Exception e) {
                errors.add("schedule.cron inválido: " + config.schedule().cron());
            }
        }

        if (config.retention() != null && "KEEP_DAYS".equals(config.retention().mode())) {
            if (config.retention().days() == null || config.retention().days() <= 0) {
                errors.add("retention.days deve ser maior que zero quando retention.mode=KEEP_DAYS");
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
