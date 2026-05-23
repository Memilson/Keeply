package com.keeply.backend.service;

import com.keeply.backend.dto.SnapshotDtos;
import com.keeply.backend.model.Snapshot;
import com.keeply.backend.model.SnapshotStatus;
import com.keeply.backend.repository.DeviceRepository;
import com.keeply.backend.repository.SnapshotRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class SnapshotService {
    private final SnapshotRepository snapshots;
    private final DeviceRepository devices;
    private final ObjectStorageService storage;

    public SnapshotService(SnapshotRepository snapshots, DeviceRepository devices, ObjectStorageService storage) {
        this.snapshots = snapshots;
        this.devices = devices;
        this.storage = storage;
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

        return toResponse(s);
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
