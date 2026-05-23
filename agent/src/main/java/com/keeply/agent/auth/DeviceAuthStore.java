package com.keeply.agent.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.keeply.agent.model.DeviceSession;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Base64;
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
            String encrypted = Files.readString(authPath);
            if (encrypted.startsWith("{")) {
                // Legado: migrar texto puro para criptografado
                DeviceSession legacy = mapper.readValue(encrypted, DeviceSession.class);
                save(legacy);
                return Optional.of(legacy);
            }
            
            String decrypted = decrypt(encrypted);
            DeviceSession session = mapper.readValue(decrypted, DeviceSession.class);
            return Optional.ofNullable(session);
        } catch (Exception e) {
            // Se falhar a decriptação (ex: trocou ID de instalação), limpa a sessão
            clear();
            return Optional.empty();
        }
    }

    public void save(DeviceSession session) {
        try {
            Files.createDirectories(authPath.getParent());
            String json = mapper.writeValueAsString(session);
            String encrypted = encrypt(json);
            Files.writeString(authPath, encrypted);
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao salvar sessão local protegida", e);
        }
    }

    public void clear() {
        try {
            Files.deleteIfExists(authPath);
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao remover sessão local", e);
        }
    }

    private String encrypt(String data) throws Exception {
        Cipher cipher = Cipher.getInstance("AES");
        cipher.init(Cipher.ENCRYPT_MODE, getSecretKey());
        byte[] encrypted = cipher.doFinal(data.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(encrypted);
    }

    private String decrypt(String encrypted) throws Exception {
        Cipher cipher = Cipher.getInstance("AES");
        cipher.init(Cipher.DECRYPT_MODE, getSecretKey());
        byte[] decoded = Base64.getDecoder().decode(encrypted);
        byte[] decrypted = cipher.doFinal(decoded);
        return new String(decrypted, StandardCharsets.UTF_8);
    }

    private SecretKeySpec getSecretKey() throws Exception {
        // Deriva uma chave AES de 128 bits a partir do ID de instalação do dispositivo
        String installationId = DeviceIdentity.getOrCreate();
        byte[] key = installationId.getBytes(StandardCharsets.UTF_8);
        MessageDigest sha = MessageDigest.getInstance("SHA-1");
        key = sha.digest(key);
        key = java.util.Arrays.copyOf(key, 16); 
        return new SecretKeySpec(key, "AES");
    }
}
