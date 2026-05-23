/* Serviço responsável pelo gerenciamento de snapshots, incluindo criação, finalização, processamento de metadados e persistência dos dados relativos aos arquivos. */
package com.keeply.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.keeply.backend.dto.ManifestParsingDtos;
import com.keeply.backend.dto.SnapshotDtos;
import com.keeply.backend.model.*;
import com.keeply.backend.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class SnapshotService {
    private final SnapshotRepository snapshots;
    private final DeviceRepository devices;
    private final ObjectStorageService storage;
    private final SnapshotFileRepository snapshotFileRepository;
    private final FileChunkRepository fileChunkRepository;
    private final ChunkRepository chunkRepository;
    private final ObjectMapper mapper;

    public SnapshotService(SnapshotRepository snapshots,
                           DeviceRepository devices,
                           ObjectStorageService storage,
                           SnapshotFileRepository snapshotFileRepository,
                           FileChunkRepository fileChunkRepository,
                           ChunkRepository chunkRepository,
                           ObjectMapper mapper) {
        this.snapshots = snapshots;
        this.devices = devices;
        this.storage = storage;
        this.snapshotFileRepository = snapshotFileRepository;
        this.fileChunkRepository = fileChunkRepository;
        this.chunkRepository = chunkRepository;
        this.mapper = mapper;
    }

    @Transactional
    public SnapshotDtos.SnapshotResponse start(UUID userId, SnapshotDtos.StartSnapshotRequest request) {
        devices.findByIdAndUserId(request.deviceId(), userId)
                .orElseThrow(() -> new IllegalArgumentException("Device inválido"));

        Snapshot s = new Snapshot();
        s.userId = userId;
        s.deviceId = request.deviceId();
        s.sourcePath = request.sourcePath();
        s.status = SnapshotStatus.IN_PROGRESS;
        s.startedAt = Instant.now();
        snapshots.save(s);
        return toResponse(s);
    }

    @Transactional
    public SnapshotDtos.SnapshotResponse complete(UUID userId, UUID snapshotId, SnapshotDtos.CompleteSnapshotRequest request) {
        Snapshot s = findOwned(userId, snapshotId);
        if (s.status != SnapshotStatus.IN_PROGRESS) {
            throw new IllegalStateException("Snapshot não está em progresso");
        }

        String key = "users/%s/manifests/%s.json".formatted(userId, snapshotId);
        storage.put(key, request.manifestJson().getBytes(StandardCharsets.UTF_8), "application/json");

        s.totalFiles = request.totalFiles();
        s.totalOriginalSize = request.totalOriginalSize();
        s.totalCompressedSize = request.totalCompressedSize();
        s.manifestKey = key;
        s.status = SnapshotStatus.COMPLETED;
        s.completedAt = Instant.now();

        try {
            persistManifestMetadata(userId, s, request.manifestJson());
        } catch (Exception e) {
            String detail = e.getMessage() == null ? "sem detalhe" : e.getMessage();
            throw new IllegalStateException("Falha ao processar metadados do manifesto no banco: " + detail, e);
        }

        return toResponse(s);
    }

    private void persistManifestMetadata(UUID userId, Snapshot snapshot, String manifestJson) throws Exception {
        ManifestParsingDtos.SnapshotManifest manifest = mapper.readValue(manifestJson, ManifestParsingDtos.SnapshotManifest.class);

        Set<String> allHashes = manifest.files().stream()
                .flatMap(f -> f.chunks().stream())
                .map(ManifestParsingDtos.ManifestChunk::hash)
                .collect(Collectors.toSet());

        Map<String, UUID> chunkMap = chunkRepository.findByUserIdAndHashIn(userId, allHashes).stream()
                .collect(Collectors.toMap(c -> c.hash, c -> c.id));

        for (ManifestParsingDtos.FileManifest fm : manifest.files()) {
            SnapshotFile file = new SnapshotFile();
            file.snapshotId = snapshot.id;
            file.relativePath = fm.path();
            file.size = fm.size();
            file.sha256 = fm.sha256();
            file.lastModified = fm.lastModified();
            snapshotFileRepository.save(file);

            for (int i = 0; i < fm.chunks().size(); i++) {
                ManifestParsingDtos.ManifestChunk mc = fm.chunks().get(i);
                UUID chunkId = chunkMap.get(mc.hash());
                if (chunkId == null) {
                    throw new IllegalStateException("Chunk %s não encontrado no banco de dados para o usuário %s".formatted(mc.hash(), userId));
                }

                FileChunk fc = new FileChunk();
                fc.snapshotFileId = file.id;
                fc.chunkId = chunkId;
                fc.chunkIndex = i;
                fc.originalSize = mc.originalSize();
                fc.compressedSize = mc.compressedSize();
                fileChunkRepository.save(fc);
            }
        }
    }

    @Transactional
    public SnapshotDtos.SnapshotResponse fail(UUID userId, UUID snapshotId, SnapshotDtos.FailSnapshotRequest request) {
        Snapshot s = findOwned(userId, snapshotId);
        s.status = SnapshotStatus.FAILED;
        s.errorMessage = request.errorMessage();
        s.completedAt = Instant.now();
        return toResponse(s);
    }

    @Transactional(readOnly = true)
    public List<SnapshotDtos.SnapshotResponse> list(UUID userId) {
        return snapshots.findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public String manifest(UUID userId, UUID snapshotId) {
        Snapshot s = findOwned(userId, snapshotId);
        if (s.status != SnapshotStatus.COMPLETED) {
            throw new IllegalStateException("Somente snapshots COMPLETED podem ser restaurados");
        }
        return new String(storage.get(s.manifestKey), StandardCharsets.UTF_8);
    }

    private Snapshot findOwned(UUID userId, UUID snapshotId) {
        return snapshots.findByIdAndUserId(snapshotId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Snapshot não encontrado"));
    }

    private SnapshotDtos.SnapshotResponse toResponse(Snapshot s) {
        return new SnapshotDtos.SnapshotResponse(
                s.id,
                s.deviceId,
                s.status,
                s.sourcePath,
                s.totalFiles,
                s.totalOriginalSize,
                s.totalCompressedSize,
                s.startedAt,
                s.completedAt,
                s.errorMessage
        );
    }
}
