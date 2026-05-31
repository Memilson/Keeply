/* DTOs para comunicação de operações de verificação e upload de pedaços de arquivos (chunks). */
package com.keeply.backend.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public final class ChunkDtos {
    private ChunkDtos() {}

    public record CheckChunksRequest(
            @NotEmpty @Size(max = 1000)
            List<@Pattern(regexp = "^[a-fA-F0-9]{64}$") String> hashes
    ) {}
    public record ChunkMetadata(String hash, long originalSize, long storedSize,
                                String compressionAlgorithm, Integer compressionLevel) {}
    public record CheckChunksResponse(List<ChunkMetadata> existing, List<String> missing) {}
    public record StorageUsageResponse(long usedBytes) {}
    public record ChunkUploadResponse(String hash, boolean stored) {}
}
