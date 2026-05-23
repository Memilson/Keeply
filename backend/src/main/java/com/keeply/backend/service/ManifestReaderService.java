package com.keeply.backend.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.keeply.backend.dto.ManifestParsingDtos;
import com.keeply.backend.dto.SnapshotDtos;
import com.keeply.backend.model.Snapshot;
import com.keeply.backend.repository.SnapshotRepository;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPInputStream;

@Service
public class ManifestReaderService {
    private final ObjectStorageService storage;
    private final SnapshotRepository snapshots;
    private final ObjectMapper mapper;

    // Cache para armazenar a lista de arquivos de snapshots acessados recentemente
    private final Cache<UUID, List<ManifestParsingDtos.FileManifest>> manifestCache = Caffeine.newBuilder()
            .maximumSize(100)
            .expireAfterAccess(5, TimeUnit.MINUTES)
            .build();

    public ManifestReaderService(ObjectStorageService storage, SnapshotRepository snapshots, ObjectMapper mapper) {
        this.storage = storage;
        this.snapshots = snapshots;
        this.mapper = mapper;
    }

    public SnapshotDtos.SnapshotFileListResponse listFiles(UUID userId, UUID snapshotId, int page, int size, String search) {
        Snapshot snapshot = snapshots.findByIdAndUserId(snapshotId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Snapshot não encontrado"));

        if (!"COMPLETED".equals(snapshot.status.name())) {
            throw new IllegalStateException("Snapshot ainda não concluído");
        }

        List<ManifestParsingDtos.FileManifest> allFiles = manifestCache.get(snapshotId, id -> loadManifest(snapshot.manifestKey));

        // Filtragem (search)
        List<ManifestParsingDtos.FileManifest> filtered = (search == null || search.isBlank())
                ? allFiles
                : allFiles.stream()
                .filter(f -> f.path().toLowerCase().contains(search.toLowerCase()))
                .toList();

        // Paginação
        int totalElements = filtered.size();
        int start = Math.min(page * size, totalElements);
        int end = Math.min(start + size, totalElements);

        List<SnapshotDtos.SnapshotFileItem> items = filtered.subList(start, end).stream()
                .map(f -> new SnapshotDtos.SnapshotFileItem(
                        f.path(),
                        f.size(),
                        f.lastModified()
                ))
                .toList();

        return new SnapshotDtos.SnapshotFileListResponse(
                items,
                new SnapshotDtos.PageMetadata(totalElements, page, size)
        );
    }

    private List<ManifestParsingDtos.FileManifest> loadManifest(String key) {
        try {
            byte[] data = storage.get(key);
            try (GZIPInputStream gis = new GZIPInputStream(new ByteArrayInputStream(data))) {
                ManifestParsingDtos.SnapshotManifest manifest = mapper.readValue(
                        gis,
                        ManifestParsingDtos.SnapshotManifest.class
                );
                // Ordenar por caminho para consistência na paginação
                return manifest.files().stream()
                        .sorted(Comparator.comparing(ManifestParsingDtos.FileManifest::path))
                        .toList();
            }
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao ler manifesto do MinIO: " + key, e);
        }
    }
}
