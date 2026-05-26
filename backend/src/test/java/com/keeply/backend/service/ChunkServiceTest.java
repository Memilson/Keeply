package com.keeply.backend.service;

import com.keeply.backend.dto.ChunkDtos;
import com.keeply.backend.model.ChunkEntity;
import com.keeply.backend.repository.ChunkRepository;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChunkServiceTest {
    @Mock
    private ChunkRepository chunkRepository;
    @Mock
    private ObjectStorageService storage;
    @InjectMocks
    private ChunkService chunkService;

    private Validator validator;
    private UUID userId;

    @BeforeEach
    void setUp() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
        userId = UUID.randomUUID();
    }

    @Test
    void uploadBatchStoresValidItems() {
        ChunkDtos.ChunkUploadItem item1 = item(hash("a"), gzip("chunk-1"));
        ChunkDtos.ChunkUploadItem item2 = item(hash("b"), gzip("chunk-2"));
        when(chunkRepository.findByUserIdAndHash(any(), anyString())).thenReturn(Optional.empty());
        when(chunkRepository.save(any(ChunkEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        ChunkDtos.ChunkUploadBatchResponse response = chunkService.uploadBatch(userId, List.of(item1, item2));

        assertEquals(2, response.results().size());
        assertTrue(response.results().stream().allMatch(ChunkDtos.ChunkUploadItemResult::stored));
        assertTrue(response.results().stream().allMatch(r -> r.error() == null));
        verify(storage, times(2)).put(anyString(), any(), anyLong(), anyString());
        verify(chunkRepository, times(2)).save(any(ChunkEntity.class));
    }

    @Test
    void uploadBatchReturnsStoredFalseForDuplicate() {
        ChunkEntity existing = new ChunkEntity();
        existing.hash = hash("a");
        when(chunkRepository.findByUserIdAndHash(any(), anyString())).thenReturn(Optional.of(existing));

        ChunkDtos.ChunkUploadBatchResponse response = chunkService.uploadBatch(userId, List.of(item(hash("a"), gzip("chunk"))));

        assertEquals(1, response.results().size());
        assertFalse(response.results().get(0).stored());
        assertNull(response.results().get(0).error());
        verify(storage, never()).put(anyString(), any(), anyLong(), anyString());
    }

    @Test
    void uploadBatchReturnsPerItemErrorForInvalidItem() {
        ChunkDtos.ChunkUploadItem invalid = new ChunkDtos.ChunkUploadItem(hash("a"), 100, 4, "###");
        when(chunkRepository.findByUserIdAndHash(any(), anyString())).thenReturn(Optional.empty());

        ChunkDtos.ChunkUploadBatchResponse response = chunkService.uploadBatch(userId, List.of(invalid));

        assertEquals(1, response.results().size());
        assertFalse(response.results().get(0).stored());
        assertTrue(response.results().get(0).error().contains("compressedBytesBase64"));
    }

    @Test
    void uploadBatchRequestRejectsMoreThan100Items() {
        List<ChunkDtos.ChunkUploadItem> items = java.util.stream.IntStream.range(0, 101)
                .mapToObj(i -> item(hash(Integer.toHexString(i)), gzip("x-" + i)))
                .toList();

        ChunkDtos.ChunkUploadBatchRequest request = new ChunkDtos.ChunkUploadBatchRequest(items);
        assertFalse(validator.validate(request).isEmpty());
    }

    private static ChunkDtos.ChunkUploadItem item(String hash, byte[] bytes) {
        return new ChunkDtos.ChunkUploadItem(
                hash,
                bytes.length + 10L,
                bytes.length,
                Base64.getEncoder().encodeToString(bytes)
        );
    }

    private static String hash(String value) {
        return value.repeat(64).substring(0, 64);
    }

    private static byte[] gzip(String content) {
        return content.getBytes();
    }
}
