package com.keeply.agent.core;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;

public final class Sha256Hasher {
    private Sha256Hasher() {}

    public static String hashBytes(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return toHex(md.digest(data));
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao calcular SHA-256", e);
        }
    }

    public static String hashFile(Path file) {
        try (InputStream in = Files.newInputStream(file)) {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[1024 * 1024];
            int read;
            while ((read = in.read(buffer)) != -1) {
                md.update(buffer, 0, read);
            }
            return toHex(md.digest());
        } catch (Exception e) {
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
