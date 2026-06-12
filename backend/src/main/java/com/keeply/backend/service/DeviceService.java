package com.keeply.backend.service;

import com.keeply.backend.dto.DeviceDtos;
import com.keeply.backend.model.Device;
import com.keeply.backend.model.Snapshot;
import com.keeply.backend.repository.AuditLogRepository;
import com.keeply.backend.repository.DeviceRepository;
import com.keeply.backend.repository.FileChunkRepository;
import com.keeply.backend.repository.ProtectionPlanRepository;
import com.keeply.backend.repository.RestoreJobRepository;
import com.keeply.backend.repository.SnapshotFileRepository;
import com.keeply.backend.repository.SnapshotRepository;
import com.keeply.backend.repository.TransferSessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class DeviceService {
    private final DeviceRepository devices;
    private final com.keeply.backend.repository.UserRepository users;
    private final ObjectStorageService storage;
    private final SnapshotRepository snapshots;
    private final SnapshotFileRepository snapshotFiles;
    private final FileChunkRepository fileChunks;
    private final TransferSessionRepository transferSessions;
    private final RestoreJobRepository restoreJobs;
    private final ProtectionPlanRepository protectionPlans;
    private final AuditLogRepository auditLogs;

    public DeviceService(DeviceRepository devices,
                         com.keeply.backend.repository.UserRepository users,
                         ObjectStorageService storage,
                         SnapshotRepository snapshots,
                         SnapshotFileRepository snapshotFiles,
                         FileChunkRepository fileChunks,
                         TransferSessionRepository transferSessions,
                         RestoreJobRepository restoreJobs,
                         ProtectionPlanRepository protectionPlans,
                         AuditLogRepository auditLogs) {
        this.devices = devices;
        this.users = users;
        this.storage = storage;
        this.snapshots = snapshots;
        this.snapshotFiles = snapshotFiles;
        this.fileChunks = fileChunks;
        this.transferSessions = transferSessions;
        this.restoreJobs = restoreJobs;
        this.protectionPlans = protectionPlans;
        this.auditLogs = auditLogs;
    }

    @Transactional
    public DeviceDtos.DeviceResponse register(UUID userId, DeviceDtos.RegisterDeviceRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Corpo da requisição é obrigatório");
        }
        if (request.hostname() == null || request.hostname().isBlank()) {
            throw new IllegalArgumentException("hostname é obrigatório");
        }

        com.keeply.backend.model.UserAccount user = users.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Usuário não encontrado"));

        Device d = new Device();
        d.user = user;
        d.name = request.name() == null || request.name().isBlank() ? request.hostname().trim() : request.name().trim();
        d.hostname = request.hostname().trim();
        d.osName = request.osName();
        d.agentVersion = request.agentVersion();
        d.deviceInstallationId = UUID.randomUUID().toString();
        d.lastSeenAt = Instant.now();
        devices.save(d);
        return toResponse(d);
    }

    @Transactional(readOnly = true)
    public List<DeviceDtos.DeviceResponse> list(UUID userId) {
        return devices.findByUserId(userId).stream().map(this::toResponse).toList();
    }

    @Transactional
    public void heartbeat(UUID userId, UUID deviceId) {
        Device d = devices.findByIdAndUserId(deviceId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Device não encontrado"));
        d.lastSeenAt = Instant.now();
    }

    @Transactional
    public void delete(UUID userId, UUID deviceId) {
        Device device = devices.findByIdAndUserId(deviceId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Device não encontrado"));

        List<Snapshot> deviceSnapshots = snapshots.findByDeviceIdOrderByCreatedAtDesc(deviceId);
        for (Snapshot snapshot : deviceSnapshots) {
            if (snapshot.manifestKey != null && !snapshot.manifestKey.isBlank()) {
                storage.delete(snapshot.manifestKey);
            }

            List<UUID> snapshotFileIds = snapshotFiles.findIdsBySnapshotId(snapshot.id);
            if (!snapshotFileIds.isEmpty()) {
                fileChunks.deleteBySnapshotFileIdIn(snapshotFileIds);
            }
            snapshotFiles.deleteBySnapshotId(snapshot.id);
            transferSessions.deleteBySnapshotId(snapshot.id);
            restoreJobs.deleteBySnapshot_Id(snapshot.id);
            snapshots.delete(snapshot);
        }

        transferSessions.deleteByDeviceId(deviceId);
        protectionPlans.deleteByDeviceId(deviceId);
        auditLogs.deleteByDeviceId(deviceId);
        devices.delete(device);
    }

    private DeviceDtos.DeviceResponse toResponse(Device d) {
        return new DeviceDtos.DeviceResponse(d.id, d.name, d.hostname, d.osName, d.agentVersion, d.lastSeenAt);
    }
}
