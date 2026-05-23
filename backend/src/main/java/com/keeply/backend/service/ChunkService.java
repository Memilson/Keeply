package com.keeply.backend.service;

import com.keeply.backend.dto.ChunkDtos;
import com.keeply.backend.model.ChunkEntity;
import com.keeply.backend.repository.ChunkRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class ChunkService {
    private final ChunkRepository chunks;
    private final ObjectStorageService storage;

    public ChunkService(ChunkRepository chunks, ObjectStorageService storage) {
        this.chunks = chunks;
        this.storage = storage;
    }

    @Transactional(readOnly = true)
    public ChunkDtos.CheckChunksResponse check(UUID userId, List<String> hashes) {
        Set<String> existing = new HashSet<>(
                chunks.findByUserIdAndHashIn(userId, hashes).stream().map(c -> c.hash).toList()
        );
        List<String> missing = hashes.stream().filter(h -> !existing.contains(h)).toList();
        return new ChunkDtos.CheckChunksResponse(existing.stream().sorted().toList(), missing);
    }

    @Transactional
    public boolean upload(UUID userId, String hash, long originalSize, long compressedSize, byte[] gzipData) {
        Optional<ChunkEntity> found = chunks.findByUserIdAndHash(userId, hash);
        if (found.isPresent()) {
            return false;
        }

        String key = chunkKey(userId, hash);
        storage.put(key, gzipData, "application/gzip");

        ChunkEntity c = new ChunkEntity();
        c.userId = userId;
        c.hash = hash;
        c.originalSize = originalSize;
        c.compressedSize = compressedSize;
        c.storageKey = key;
        chunks.save(c);
        return true;
    }

    @Transactional(readOnly = true)
    public byte[] download(UUID userId, String hash) {
        ChunkEntity chunk = chunks.findByUserIdAndHash(userId, hash)
                .orElseThrow(() -> new IllegalArgumentException("Chunk não encontrado"));
        return storage.get(chunk.storageKey);
    }

    public static String chunkKey(UUID userId, String hash) {
        String a = hash.substring(0, 2);
        String b = hash.substring(2, 4);
        return "users/%s/chunks/%s/%s/%s.gz".formatted(userId, a, b, hash);
    }
}
