package com.keeply.agent.core;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;

public class ContentDefinedChunker {
    public static final int MIN_SIZE = 512 * 1024;
    public static final int AVG_SIZE = 1024 * 1024;
    public static final int MAX_SIZE = 4 * 1024 * 1024;

    private static final int CUT_MASK = AVG_SIZE - 1;

    public String process(Path file, ChunkConsumer consumer) {
        try (InputStream in = Files.newInputStream(file)) {
            MessageDigest fileDigest = MessageDigest.getInstance("SHA-256");
            ByteArrayOutputStream current = new ByteArrayOutputStream();
            int rolling = 0;
            int index = 0;
            long offset = 0;
            byte[] buffer = new byte[64 * 1024];
            int bytesRead;

            while ((bytesRead = in.read(buffer)) != -1) {
                fileDigest.update(buffer, 0, bytesRead);
                int lastCut = 0;
                for (int i = 0; i < bytesRead; i++) {
                    int b = buffer[i] & 0xFF;
                    rolling = ((rolling << 1) + b) & 0x7fffffff;

                    int currentSize = current.size() + (i - lastCut + 1);
                    boolean canCut = currentSize >= MIN_SIZE && (rolling & CUT_MASK) == 0;
                    boolean mustCut = currentSize >= MAX_SIZE;

                    if (canCut || mustCut) {
                        current.write(buffer, lastCut, i - lastCut + 1);
                        byte[] data = current.toByteArray();
                        consumer.accept(new ChunkData(index, offset, data, data.length));
                        
                        offset += data.length;
                        index++;
                        current.reset();
                        rolling = 0;
                        lastCut = i + 1;
                    }
                }
                if (lastCut < bytesRead) {
                    current.write(buffer, lastCut, bytesRead - lastCut);
                }
            }

            if (current.size() > 0) {
                byte[] data = current.toByteArray();
                consumer.accept(new ChunkData(index, offset, data, data.length));
            }

            return hex(fileDigest.digest());
        } catch (Exception e) {
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
