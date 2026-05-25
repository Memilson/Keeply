package com.keeply.agent.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.github.javakeyring.Keyring;
import com.keeply.agent.model.DeviceSession;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.util.Base64;
import java.util.Optional;

/**
 * Armazena a sessão do dispositivo localmente com criptografia forte.
 * Utiliza AES/GCM/NoPadding (256 bits).
 * A chave mestre é armazenada no chaveiro do sistema operacional (Keychain/DPAPI/SecretService).
 * Se o chaveiro não estiver disponível, utiliza PBKDF2 com o ID do dispositivo como base.
 */
public class DeviceAuthStore {
    private static final String V2_PREFIX = "v2:";
    private static final int AES_KEY_SIZE = 256;
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;
    private static final int PBKDF2_ITERATIONS = 600000;
    private static final int PBKDF2_SALT_LENGTH = 16;

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
            String content = Files.readString(authPath).trim();
            if (content.isBlank()) return Optional.empty();

            if (content.startsWith("{")) {
                // Legado: Texto puro (apenas em dev muito antigo)
                DeviceSession legacy = mapper.readValue(content, DeviceSession.class);
                save(legacy);
                return Optional.of(legacy);
            }

            if (!content.startsWith(V2_PREFIX)) {
                // Legado V1: AES/ECB (formato base64 sem prefixo)
                try {
                    String decrypted = decryptV1(content);
                    DeviceSession session = mapper.readValue(decrypted, DeviceSession.class);
                    save(session); // Migra para V2
                    return Optional.of(session);
                } catch (Exception e) {
                    // Se falhar a decriptação V1, limpa e força novo login
                    clear();
                    return Optional.empty();
                }
            }

            // Formato V2: v2:[S_TYPE]:[BASE64(SALT? + IV + CIPHERTEXT + TAG)]
            String decrypted = decryptV2(content.substring(V2_PREFIX.length()));
            DeviceSession session = mapper.readValue(decrypted, DeviceSession.class);
            return Optional.ofNullable(session);
        } catch (Exception e) {
            clear();
            return Optional.empty();
        }
    }

    public void save(DeviceSession session) {
        try {
            Files.createDirectories(authPath.getParent());
            String json = mapper.writeValueAsString(session);
            String encrypted = encryptV2(json);
            Files.writeString(authPath, V2_PREFIX + encrypted);
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

    private String encryptV2(String data) throws Exception {
        byte[] salt = new byte[PBKDF2_SALT_LENGTH];
        new SecureRandom().nextBytes(salt);
        
        SecretKey key = getMasterKey(salt);
        byte[] iv = new byte[GCM_IV_LENGTH];
        new SecureRandom().nextBytes(iv);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
        
        byte[] ciphertext = cipher.doFinal(data.getBytes(StandardCharsets.UTF_8));
        
        // Estrutura: [SALT] + [IV] + [CIPHERTEXT]
        ByteBuffer bb = ByteBuffer.allocate(salt.length + iv.length + ciphertext.length);
        bb.put(salt);
        bb.put(iv);
        bb.put(ciphertext);
        
        String storageType = isKeyringAvailable() ? "K" : "P";
        return storageType + ":" + Base64.getEncoder().encodeToString(bb.array());
    }

    private String decryptV2(String data) throws Exception {
        String[] parts = data.split(":");
        String type = parts[0];
        byte[] combined = Base64.getDecoder().decode(parts[1]);
        
        ByteBuffer bb = ByteBuffer.wrap(combined);
        byte[] salt = new byte[PBKDF2_SALT_LENGTH];
        bb.get(salt);
        
        byte[] iv = new byte[GCM_IV_LENGTH];
        bb.get(iv);
        
        byte[] ciphertext = new byte[bb.remaining()];
        bb.get(ciphertext);

        SecretKey key = getMasterKey(salt);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
        
        byte[] decrypted = cipher.doFinal(ciphertext);
        return new String(decrypted, StandardCharsets.UTF_8);
    }

    private SecretKey getMasterKey(byte[] salt) throws Exception {
        String installationId = DeviceIdentity.getOrCreate();
        
        if (isKeyringAvailable()) {
            try (Keyring keyring = Keyring.create()) {
                String service = "KeeplyAgent";
                String account = "MasterKey_" + installationId;
                
                String storedKey = keyring.getPassword(service, account);
                if (storedKey == null) {
                    byte[] rawKey = new byte[32];
                    new SecureRandom().nextBytes(rawKey);
                    storedKey = Base64.getEncoder().encodeToString(rawKey);
                    keyring.setPassword(service, account, storedKey);
                }
                
                byte[] decodedKey = Base64.getDecoder().decode(storedKey);
                return new SecretKeySpec(decodedKey, "AES");
            } catch (Exception e) {
                // Fallback para PBKDF2 se o chaveiro falhar
            }
        }

        // Fallback PBKDF2: Chave derivada do installationId + salt
        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        KeySpec spec = new PBEKeySpec(installationId.toCharArray(), salt, PBKDF2_ITERATIONS, AES_KEY_SIZE);
        return new SecretKeySpec(factory.generateSecret(spec).getEncoded(), "AES");
    }

    private boolean isKeyringAvailable() {
        try (Keyring keyring = Keyring.create()) {
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // --- Métodos de compatibilidade V1 ---

    private String decryptV1(String encrypted) throws Exception {
        Cipher cipher = Cipher.getInstance("AES");
        String installationId = DeviceIdentity.getOrCreate();
        byte[] keyBytes = java.util.Arrays.copyOf(java.security.MessageDigest.getInstance("SHA-1")
                .digest(installationId.getBytes(StandardCharsets.UTF_8)), 16);
        SecretKeySpec key = new SecretKeySpec(keyBytes, "AES");
        
        cipher.init(Cipher.DECRYPT_MODE, key);
        byte[] decoded = Base64.getDecoder().decode(encrypted);
        byte[] decrypted = cipher.doFinal(decoded);
        return new String(decrypted, StandardCharsets.UTF_8);
    }
}
