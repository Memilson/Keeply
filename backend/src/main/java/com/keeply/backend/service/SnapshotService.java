/* Serviço responsável pelo gerenciamento de snapshots, incluindo criação, finalização, processamento de metadados e persistência dos dados relativos aos arquivos. */
package com.keeply.backend.service;

import com.keeply.backend.dto.SnapshotDtos;
import com.keeply.backend.model.*;
import com.keeply.backend.repository.*;
import com.keeply.backend.security.JwtPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Service
public class SnapshotService {
    private static final Logger log = LoggerFactory.getLogger(SnapshotService.class);
    private final SnapshotRepository snapshots;
    private final DeviceRepository devices;
    private final ObjectStorageService storage;
    private final SnapshotFileRepository snapshotFiles;
    private final FileChunkRepository fileChunks;
    private final TransferSessionRepository transferSessions;
    private final RestoreJobRepository restoreJobs;
    private final ManifestParserService manifestParser;
    private final TransferCredentialBroker transferBroker;

    public SnapshotService(SnapshotRepository snapshots,
                           DeviceRepository devices,
                           ObjectStorageService storage,
                           SnapshotFileRepository snapshotFiles,
                           FileChunkRepository fileChunks,
                           TransferSessionRepository transferSessions,
                           RestoreJobRepository restoreJobs,
                           ManifestParserService manifestParser,
                           TransferCredentialBroker transferBroker) {
        this.snapshots = snapshots;
        this.devices = devices;
        this.storage = storage;
        this.snapshotFiles = snapshotFiles;
        this.fileChunks = fileChunks;
        this.transferSessions = transferSessions;
        this.restoreJobs = restoreJobs;
        this.manifestParser = manifestParser;
        this.transferBroker = transferBroker;
    }

    @Transactional
    public SnapshotDtos.StartSnapshotResponse start(JwtPrincipal principal, SnapshotDtos.StartSnapshotRequest request) {
        log.info("Iniciando novo snapshot para o device: {} (User: {})", request.deviceId(), principal.userId());
        Device device = devices.findByIdAndUserId(request.deviceId(), principal.userId())
                .orElseThrow(() -> new IllegalArgumentException("Device inválido"));

        if (snapshots.existsByDeviceIdAndStatusIn(device.id, List.of(SnapshotStatus.IN_PROGRESS, SnapshotStatus.PROCESSING))) {
            throw new IllegalStateException("Já existe um snapshot em execução para este dispositivo");
        }

        Snapshot s = new Snapshot();
        s.device = device;
        s.sourcePath = request.sourcePath();
        s.status = SnapshotStatus.IN_PROGRESS;
        s.startedAt = Instant.now();
        snapshots.save(s);
        return new SnapshotDtos.StartSnapshotResponse(
                toResponse(s),
                transferBroker.openBackup(principal, request.deviceId(), s.id)
        );
    }

    @Transactional
    public SnapshotDtos.SnapshotResponse complete(JwtPrincipal principal, UUID snapshotId,
                                                   SnapshotDtos.CompleteSnapshotRequest request) {
        log.info("Finalizando uploads do snapshot: {} (User: {})", snapshotId, principal.userId());
        Snapshot s = findOwned(principal.userId(), snapshotId);
        if (s.status != SnapshotStatus.IN_PROGRESS) {
            throw new IllegalStateException("Snapshot não está em progresso");
        }

        TransferSession session = transferBroker.processing(principal, request.transferSessionId(), snapshotId);
        String stagedManifest = session.stagingPrefix + "manifest.json.zst";
        if (!storage.exists(stagedManifest)) {
            throw new IllegalStateException("Manifesto staged não encontrado");
        }

        s.totalFiles = request.totalFiles();
        s.totalOriginalSize = request.totalOriginalSize();
        s.totalCompressedSize = request.totalCompressedSize();
        String manifestKey = "users/%s/manifests/%s.json.zst".formatted(principal.userId(), snapshotId);
        s.manifestKey = manifestKey;
        s.status = SnapshotStatus.PROCESSING;
        s.completedAt = Instant.now();
        snapshots.save(s);

        manifestParser.auditAndPromoteAsync(s.id, session.id, session.stagingPrefix, principal.userId(), manifestKey);

        return toResponse(s);
    }

    @Transactional
    public SnapshotDtos.SnapshotResponse fail(UUID userId, UUID snapshotId, SnapshotDtos.FailSnapshotRequest request) {
        Snapshot s = findOwned(userId, snapshotId);
        s.status = SnapshotStatus.FAILED;
        s.errorMessage = request.errorMessage();
        s.completedAt = Instant.now();
        snapshots.save(s); // VULN-009: persistência explícita — não depende do dirty checking do JPA
        return toResponse(s);
    }

    @Transactional(readOnly = true)
    public SnapshotDtos.SnapshotResponse get(UUID userId, UUID snapshotId) {
        return toResponse(findOwned(userId, snapshotId));
    }

    @Transactional(readOnly = true)
    public SnapshotDtos.PagedSnapshotResponse list(UUID userId, int page, int size) {
        var pageable = org.springframework.data.domain.PageRequest.of(page, size);
        var result = snapshots.findByDeviceUserIdOrderByCreatedAtDesc(userId, pageable);
        var items = result.getContent().stream().map(this::toResponse).toList();
        return new SnapshotDtos.PagedSnapshotResponse(
                items,
                new SnapshotDtos.PageMetadata(result.getTotalElements(), page, size)
        );
    }

    @Transactional
    public void delete(UUID userId, UUID snapshotId) {
        Snapshot snapshot = findOwned(userId, snapshotId);
        if (snapshot.manifestKey != null && !snapshot.manifestKey.isBlank()) {
            storage.delete(snapshot.manifestKey);
        }

        List<UUID> snapshotFileIds = snapshotFiles.findIdsBySnapshotId(snapshotId);
        if (!snapshotFileIds.isEmpty()) {
            fileChunks.deleteBySnapshotFileIdIn(snapshotFileIds);
        }
        snapshotFiles.deleteBySnapshotId(snapshotId);
        transferSessions.deleteBySnapshotId(snapshotId);
        restoreJobs.deleteBySnapshot_Id(snapshotId);
        snapshots.delete(snapshot);
    }

    @Transactional(readOnly = true)
    public void assertRestorable(UUID userId, UUID snapshotId) {
        Snapshot s = findOwned(userId, snapshotId);
        if (s.status != SnapshotStatus.COMPLETED) {
            throw new IllegalStateException("Somente snapshots concluídos podem ser restaurados");
        }
    }

    private Snapshot findOwned(UUID userId, UUID snapshotId) {
        return snapshots.findByIdAndDeviceUserId(snapshotId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Snapshot não encontrado"));
    }

    private SnapshotDtos.SnapshotResponse toResponse(Snapshot s) {
        UUID deviceId = s.device != null ? s.device.id : null;
        long totalFiles = s.totalFiles != null ? s.totalFiles : 0L;
        long totalOriginalSize = s.totalOriginalSize != null ? s.totalOriginalSize : 0L;
        long totalCompressedSize = s.totalCompressedSize != null ? s.totalCompressedSize : 0L;

        if ((s.totalFiles == null || s.totalOriginalSize == null) && s.id != null) {
            totalFiles = snapshotFiles.countBySnapshotIdAgg(s.id);
            totalOriginalSize = snapshotFiles.sumSizeBySnapshotIdAgg(s.id);
        }
        if (s.totalCompressedSize == null && s.id != null) {
            totalCompressedSize = fileChunks.sumCompressedSizeBySnapshotIdAgg(s.id);
        }
        
        return new SnapshotDtos.SnapshotResponse(
                s.id,
                deviceId,
                s.status,
                s.sourcePath,
                totalFiles,
                totalOriginalSize,
                totalCompressedSize,
                s.startedAt,
                s.completedAt,
                s.errorMessage
        );
    }
}
