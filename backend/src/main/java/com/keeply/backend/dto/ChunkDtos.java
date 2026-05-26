/* DTOs para comunicação de operações de verificação e upload de pedaços de arquivos (chunks). */
package com.keeply.backend.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

public final class ChunkDtos {
    private ChunkDtos() {}

    public record CheckChunksRequest(
            @NotEmpty @Size(max = 1000) 
            List<@Pattern(regexp = "^[a-fA-F0-9]{64}$") String> hashes
    ) {}
    public record CheckChunksResponse(List<String> existing, List<String> missing) {}
    public record ChunkUploadBatchRequest(
            @NotEmpty @Size(max = 100)
            List<@Valid ChunkUploadItem> items
    ) {}
    public record ChunkUploadItem(
            @NotNull @Pattern(regexp = "^[a-fA-F0-9]{64}$") String hash,
            @Positive long originalSize,
            @Positive long compressedSize,
            @NotNull @NotEmpty String compressedBytesBase64
    ) {}
    public record ChunkUploadBatchResponse(List<ChunkUploadItemResult> results) {}
    public record ChunkUploadItemResult(String hash, boolean stored, String error) {}
}
