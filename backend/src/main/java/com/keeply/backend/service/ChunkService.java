/* Serviço que gerencia o processamento, armazenamento e recuperação de chunks de arquivos, integrando-se com o ObjectStorageService. */
package com.keeply.backend.service;

import com.keeply.backend.dto.ChunkDtos;
import com.keeply.backend.model.ChunkEntity;
import com.keeply.backend.repository.ChunkRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.util.*;
import java.util.regex.Pattern;

@Service
public class ChunkService {
    private static final Pattern SHA256_HEX = Pattern.compile("^[a-fA-F0-9]{64}$");
    private final ChunkRepository chunks;
    private final ObjectStorageService storage;

    public ChunkService(ChunkRepository chunks, ObjectStorageService storage) {
        this.chunks = chunks;
        this.storage = storage;
    }

    private static void validateHash(String hash) {
        if (hash == null || !SHA256_HEX.matcher(hash).matches()) {
            throw new IllegalArgumentException("Hash SHA-256 inválido");
        }
    }

    @Transactional(readOnly = true)
    public ChunkDtos.CheckChunksResponse check(UUID userId, List<String> hashes) {
        if (hashes == null || hashes.isEmpty()) {
            return new ChunkDtos.CheckChunksResponse(List.of(), List.of());
        }
        
        hashes.forEach(ChunkService::validateHash);
        
        Set<String> existing = new HashSet<>(
                chunks.findByUserIdAndHashIn(userId, hashes).stream().map(c -> c.hash).toList()
        );
        List<String> missing = hashes.stream().filter(h -> !existing.contains(h)).toList();
        return new ChunkDtos.CheckChunksResponse(existing.stream().sorted().toList(), missing);
    }

    public boolean upload(UUID userId, String hash, long originalSize, long compressedSize, InputStream gzipStream) {
        validateHash(hash);
        
        if (originalSize <= 0) throw new IllegalArgumentException("originalSize deve ser maior que zero");
        if (compressedSize <= 0) throw new IllegalArgumentException("compressedSize deve ser maior que zero");

        Optional<ChunkEntity> found = chunks.findByUserIdAndHash(userId, hash);
        if (found.isPresent()) {
            return false;
        }

        String key = chunkKey(userId, hash);
        storage.put(key, gzipStream, compressedSize, "application/gzip");

        ChunkEntity c = new ChunkEntity();
        c.userId = userId;
        c.hash = hash.toLowerCase();
        c.originalSize = originalSize;
        c.compressedSize = compressedSize;
        c.storageKey = key;
        chunks.save(c);
        return true;
    }

    public InputStream downloadStream(UUID userId, String hash) {
        validateHash(hash);
        ChunkEntity chunk = chunks.findByUserIdAndHash(userId, hash)
                .orElseThrow(() -> new IllegalArgumentException("Chunk não encontrado"));
        return storage.getStream(chunk.storageKey);
    }

    public static String chunkKey(UUID userId, String hash) {
        validateHash(hash);
        String a = hash.substring(0, 2);
        String b = hash.substring(2, 4);
        return "users/%s/chunks/%s/%s/%s.gz".formatted(userId, a, b, hash);
    }
}
