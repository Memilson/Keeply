package com.keeply.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.keeply.backend.dto.ChunkDtos;
import com.keeply.backend.model.ChunkEntity;
import com.keeply.backend.repository.ChunkRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ChunkServiceTest {
    @Test
    void checkChunksReturnsCompressionMetadataAndStoredSize() {
        UUID userId = UUID.randomUUID();
        String hash = "a".repeat(64);
        ChunkEntity entity = new ChunkEntity();
        entity.userId = userId;
        entity.hash = hash;
        entity.originalSize = 100;
        entity.compressedSize = 40;
        entity.compressionAlgorithm = "ZSTD";
        entity.compressionLevel = 3;

        ChunkRepository repository = mock(ChunkRepository.class);
        when(repository.findByUserIdAndHashIn(userId, List.of(hash))).thenReturn(List.of(entity));

        ChunkService service = new ChunkService(repository, mock(ObjectStorageService.class));

        ChunkDtos.ChunkMetadata metadata = service.check(userId, List.of(hash)).existing().getFirst();
        assertEquals(hash, metadata.hash());
        assertEquals(40, metadata.storedSize());
        assertEquals("ZSTD", metadata.compressionAlgorithm());
        assertEquals(3, metadata.compressionLevel());
    }

    @Test
    void chunkKeyUsesZstdExtension() {
        UUID userId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        String hash = "b".repeat(64);

        assertEquals("users/00000000-0000-0000-0000-000000000001/chunks/bb/bb/" + hash + ".zst",
                ChunkService.chunkKey(userId, hash));
    }
}
