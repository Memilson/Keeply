package com.keeply.agent.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;

public final class Sha256Hasher {
    private static final Logger log = LoggerFactory.getLogger(Sha256Hasher.class);
    private Sha256Hasher() {}

    public static String hashBytes(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            String hash = toHex(md.digest(data));
            log.debug("🔑 Hash calculado para {} bytes: {}", data.length, hash);
            return hash;
        } catch (Exception e) {
            log.error("❌ Falha ao calcular SHA-256: {}", e.getMessage());
            throw new IllegalStateException("Falha ao calcular SHA-256", e);
        }
    }

    public static String hashFile(Path file) {
        log.debug("🔑 Calculando hash para o arquivo: {}", file);
        try (InputStream in = Files.newInputStream(file)) {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[1024 * 1024];
            int read;
            while ((read = in.read(buffer)) != -1) {
                md.update(buffer, 0, read);
            }
            String hash = toHex(md.digest());
            log.debug("🔑 Hash do arquivo {}: {}", file, hash);
            return hash;
        } catch (Exception e) {
            log.error("❌ Falha ao calcular SHA-256 do arquivo {}: {}", file, e.getMessage());
            throw new IllegalStateException("Falha ao calcular SHA-256 do arquivo: " + file, e);
        }
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xf, 16));
            sb.append(Character.forDigit(b & 0xf, 16));
        }
        return sb.toString();
    }
}
