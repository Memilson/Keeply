package com.keeply.agent.core;

import com.keeply.agent.model.ChunkPayload;
import com.keeply.agent.model.ManifestChunk;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

public class ContentDefinedChunker {
    public static final int MIN_SIZE = 512 * 1024;
    public static final int AVG_SIZE = 1024 * 1024;
    public static final int MAX_SIZE = 4 * 1024 * 1024;

    private static final int CUT_MASK = AVG_SIZE - 1;

    public ChunkResult chunk(Path file) {
        List<ManifestChunk> manifestChunks = new ArrayList<>();
        List<ChunkPayload> payloads = new ArrayList<>();

        try (InputStream in = Files.newInputStream(file)) {
            MessageDigest fileDigest = MessageDigest.getInstance("SHA-256");
            ByteArrayOutputStream current = new ByteArrayOutputStream();
            int rolling = 0;
            int index = 0;
            byte[] buffer = new byte[64 * 1024];
            int bytesRead;

            while ((bytesRead = in.read(buffer)) != -1) {
                fileDigest.update(buffer, 0, bytesRead);
                for (int i = 0; i < bytesRead; i++) {
                    int b = buffer[i] & 0xFF;
                    current.write(b);
                    rolling = ((rolling << 1) + b) & 0x7fffffff;

                    int size = current.size();
                    boolean canCut = size >= MIN_SIZE && (rolling & CUT_MASK) == 0;
                    boolean mustCut = size >= MAX_SIZE;

                    if (canCut || mustCut) {
                        index = flushChunk(index, current, manifestChunks, payloads);
                        rolling = 0;
                    }
                }
            }

            if (current.size() > 0) {
                flushChunk(index, current, manifestChunks, payloads);
            }

            String fileHash = hex(fileDigest.digest());
            return new ChunkResult(fileHash, manifestChunks, payloads);
        } catch (Exception e) {
            throw new IllegalStateException("Falha no chunking do arquivo: " + file, e);
        }
    }

    private int flushChunk(
            int index,
            ByteArrayOutputStream current,
            List<ManifestChunk> manifestChunks,
            List<ChunkPayload> payloads
    ) {
        byte[] original = current.toByteArray();
        String hash = Sha256Hasher.hashBytes(original);
        byte[] compressed = GzipCompressor.compress(original);

        manifestChunks.add(new ManifestChunk(index, hash, original.length, compressed.length));
        payloads.add(new ChunkPayload(hash, original.length, compressed.length, compressed));

        current.reset();
        return index + 1;
    }

    private String hex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    public record ChunkResult(String fileHash, List<ManifestChunk> manifestChunks, List<ChunkPayload> payloads) {}
}
