package com.keeply.agent.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.keeply.agent.model.DeviceSession;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

public class DeviceAuthStore {
    private final Path authPath;
    private final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .findAndRegisterModules();

    public DeviceAuthStore(Path authPath) {
        this.authPath = authPath.toAbsolutePath().normalize();
    }

    public Optional<DeviceSession> load() {
        try {
            if (!Files.exists(authPath)) {
                return Optional.empty();
            }
            DeviceSession session = mapper.readValue(Files.readString(authPath), DeviceSession.class);
            return Optional.ofNullable(session);
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao ler sessão local em " + authPath, e);
        }
    }

    public void save(DeviceSession session) {
        try {
            Files.createDirectories(authPath.getParent());
            mapper.writerWithDefaultPrettyPrinter().writeValue(authPath.toFile(), session);
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao salvar sessão local em " + authPath, e);
        }
    }

    public void clear() {
        try {
            Files.deleteIfExists(authPath);
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao remover sessão local em " + authPath, e);
        }
    }
}
