package com.keeply.backend.service;

import com.keeply.backend.dto.ChunkDtos;
import com.keeply.backend.model.ChunkEntity;
import com.keeply.backend.repository.ChunkRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChunkServiceTest {
    @Mock ChunkRepository chunkRepository;
    @Mock ObjectStorageService storage;
    private ChunkService service;
    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        service = new ChunkService(chunkRepository, storage);
    }

    @Test
    void binaryUploadStreamsGzipAndPersistsMetadata() {
        byte[] bytes = new byte[]{1, 2, 3};
        when(chunkRepository.findByUserIdAndHash(any(), anyString())).thenReturn(Optional.empty());

        ChunkDtos.ChunkUploadResponse result = service.upload(userId, hash("a"), 42,
                bytes.length, new ByteArrayInputStream(bytes));

        assertTrue(result.stored());
        verify(storage).put(anyString(), any(ByteArrayInputStream.class), eq(3L), eq("application/gzip"));
        verify(chunkRepository).save(argThat(c -> c.originalSize == 42 && c.compressedSize == 3));
    }

    @Test
    void duplicateDoesNotWriteStorage() {
        when(chunkRepository.findByUserIdAndHash(any(), anyString())).thenReturn(Optional.of(new ChunkEntity()));

        ChunkDtos.ChunkUploadResponse result = service.upload(userId, hash("b"), 1, 1,
                new ByteArrayInputStream(new byte[]{1}));

        assertFalse(result.stored());
        verify(storage, never()).put(anyString(), any(), anyLong(), anyString());
    }

    @Test
    void persistenceFailureRollsBackStoredObject() {
        when(chunkRepository.findByUserIdAndHash(any(), anyString())).thenReturn(Optional.empty());
        when(chunkRepository.save(any())).thenThrow(new IllegalStateException("db"));
        when(storage.exists(anyString())).thenReturn(true);

        assertThrows(IllegalStateException.class, () -> service.upload(userId, hash("c"), 1, 1,
                new ByteArrayInputStream(new byte[]{1})));

        verify(storage).delete(anyString());
    }

    private static String hash(String prefix) {
        return prefix.repeat(64);
    }
}
