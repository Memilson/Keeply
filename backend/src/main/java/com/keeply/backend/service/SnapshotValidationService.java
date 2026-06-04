package com.keeply.backend.service;

import com.keeply.backend.model.ChunkEntity;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

@Service
public class SnapshotValidationService {
    private final ObjectStorageService storage;

    public SnapshotValidationService(ObjectStorageService storage) {
        this.storage = storage;
    }

    public void validateUploadedChunks(UUID userId, Map<String, ManifestParserService.ChunkReference> newChunks) {
        for (ManifestParserService.ChunkReference reference : newChunks.values()) {
            String storageKey = ChunkService.chunkKey(userId, reference.hash());
            if (!storage.exists(storageKey)) {
                throw new IllegalStateException("Chunk validado ausente no storage definitivo: " + reference.hash());
            }
        }
    }

    public ChunkEntity toChunkEntity(UUID userId, ManifestParserService.ChunkReference reference) {
        ChunkEntity entity = new ChunkEntity();
        entity.userId = userId;
        entity.hash = reference.hash();
        entity.originalSize = reference.originalSize();
        entity.compressedSize = reference.compressedSize();
        entity.compressionAlgorithm = reference.compressionAlgorithm();
        entity.compressionLevel = reference.compressionLevel();
        entity.storageKey = ChunkService.chunkKey(userId, reference.hash());
        return entity;
    }
}
