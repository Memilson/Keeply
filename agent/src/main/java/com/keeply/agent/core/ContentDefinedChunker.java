package com.keeply.agent.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Arrays;

public class ContentDefinedChunker {
    private static final Logger log = LoggerFactory.getLogger(ContentDefinedChunker.class);
    public static final int MIN_SIZE = 1024 * 1024;
    public static final int AVG_SIZE = 4 * 1024 * 1024;
    public static final int MAX_SIZE = 8 * 1024 * 1024;

    private static final int CUT_MASK = AVG_SIZE - 1;

    public String process(Path file, ChunkConsumer consumer) {
        log.debug("✂️ Iniciando chunking do arquivo: {}", file);
        try (InputStream in = Files.newInputStream(file)) {
            MessageDigest fileDigest = MessageDigest.getInstance("SHA-256");
            byte[] current = new byte[MAX_SIZE];
            int currentSize = 0;
            int rolling = 0;
            int index = 0;
            long offset = 0;
            byte[] buffer = new byte[64 * 1024];
            int bytesRead;

            while ((bytesRead = in.read(buffer)) != -1) {
                fileDigest.update(buffer, 0, bytesRead);
                for (int i = 0; i < bytesRead; i++) {
                    int b = buffer[i] & 0xFF;
                    current[currentSize++] = buffer[i];
                    rolling = ((rolling << 1) + b) & 0x7fffffff;

                    boolean canCut = currentSize >= MIN_SIZE && (rolling & CUT_MASK) == 0;
                    boolean mustCut = currentSize >= MAX_SIZE;

                    if (canCut || mustCut) {
                        byte[] data = mustCut ? current : Arrays.copyOf(current, currentSize);
                        log.debug("📦 Chunk gerado: index={} size={} type={}", index, data.length, mustCut ? "MUST_CUT" : "CAN_CUT");
                        consumer.accept(new ChunkData(index, offset, data, data.length));
                        
                        offset += data.length;
                        index++;
                        current = new byte[MAX_SIZE];
                        currentSize = 0;
                        rolling = 0;
                    }
                }
            }

            if (currentSize > 0) {
                byte[] data = Arrays.copyOf(current, currentSize);
                log.debug("📦 Último chunk gerado: index={} size={}", index, data.length);
                consumer.accept(new ChunkData(index, offset, data, data.length));
            }

            String fileHash = hex(fileDigest.digest());
            log.debug("✅ Chunking concluído: chunks={} hash={}", index + 1, fileHash);
            return fileHash;
        } catch (Exception e) {
            log.error("❌ Falha no processamento (streaming) do arquivo {}: {}", file, e.getMessage());
            throw new IllegalStateException("Falha no processamento (streaming) do arquivo: " + file, e);
        }
    }

    private String hex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
