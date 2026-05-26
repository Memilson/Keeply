/* Serviço que gerencia o processamento, armazenamento e recuperação de chunks de arquivos, integrando-se com o ObjectStorageService. */
package com.keeply.backend.service;

import com.keeply.backend.dto.ChunkDtos;
import com.keeply.backend.model.ChunkEntity;
import com.keeply.backend.repository.ChunkRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.util.*;
import java.util.regex.Pattern;

@Service
public class ChunkService {
    private static final Logger log = LoggerFactory.getLogger(ChunkService.class);
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

    public static String chunkKey(UUID userId, String hash) {
        validateHash(hash);
        String a = hash.substring(0, 2);
        String b = hash.substring(2, 4);
        return "users/%s/chunks/%s/%s/%s.gz".formatted(userId, a, b, hash);
    }
}
