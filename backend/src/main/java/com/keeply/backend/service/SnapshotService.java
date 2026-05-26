/* Serviço responsável pelo gerenciamento de snapshots, incluindo criação, finalização, processamento de metadados e persistência dos dados relativos aos arquivos. */
package com.keeply.backend.service;

import com.keeply.backend.dto.SnapshotDtos;
import com.keeply.backend.model.*;
import com.keeply.backend.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.time.Instant;
import java.util.*;

@Service
public class SnapshotService {
    private static final Logger log = LoggerFactory.getLogger(SnapshotService.class);
    private final SnapshotRepository snapshots;
    private final DeviceRepository devices;
    private final ObjectStorageService storage;
    private final ManifestParserService manifestParser;

    public SnapshotService(SnapshotRepository snapshots,
                           DeviceRepository devices,
                           ObjectStorageService storage,
                           ManifestParserService manifestParser) {
        this.snapshots = snapshots;
        this.devices = devices;
        this.storage = storage;
        this.manifestParser = manifestParser;
    }

    @Transactional
    public SnapshotDtos.SnapshotResponse start(UUID userId, SnapshotDtos.StartSnapshotRequest request) {
        log.info("Iniciando novo snapshot para o device: {} (User: {})", request.deviceId(), userId);
        Device device = devices.findByIdAndUserId(request.deviceId(), userId)
                .orElseThrow(() -> new IllegalArgumentException("Device inválido"));

        Snapshot s = new Snapshot();
        s.device = device;
        s.sourcePath = request.sourcePath();
        s.status = SnapshotStatus.IN_PROGRESS;
        s.startedAt = Instant.now();
        snapshots.save(s);
        return toResponse(s);
    }

    @Transactional
    public SnapshotDtos.SnapshotResponse complete(UUID userId, UUID snapshotId, InputStream manifestGzip,
                                                   long manifestLength, long totalFiles,
                                                   long totalOriginalSize, long totalCompressedSize) {
        log.info("Finalizando snapshot: {} (User: {})", snapshotId, userId);
        Snapshot s = findOwned(userId, snapshotId);
        if (s.status != SnapshotStatus.IN_PROGRESS) {
            throw new IllegalStateException("Snapshot não está em progresso");
        }

        String key = "users/%s/manifests/%s.json.gz".formatted(userId, snapshotId);
        if (manifestLength <= 0) {
            throw new IllegalArgumentException("Manifesto vazio");
        }
        storage.put(key, manifestGzip, manifestLength, "application/gzip");

        s.totalFiles = totalFiles;
        s.totalOriginalSize = totalOriginalSize;
        s.totalCompressedSize = totalCompressedSize;
        s.manifestKey = key;
        s.status = SnapshotStatus.PROCESSING; // Novo estado: processando manifesto
        s.completedAt = Instant.now();
        snapshots.save(s);

        // Dispara o processamento assíncrono do manifesto para indexação no banco
        manifestParser.parseAsync(s.id);

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
        return snapshots.findByDeviceUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public InputStream manifest(UUID userId, UUID snapshotId) {
        Snapshot s = findOwned(userId, snapshotId);
        if (s.status != SnapshotStatus.COMPLETED && s.status != SnapshotStatus.PROCESSING) {
            throw new IllegalStateException("Somente snapshots concluídos ou em processamento podem ter o manifesto lido");
        }
        
        try {
            return new java.util.zip.GZIPInputStream(storage.getStream(s.manifestKey));
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao descompactar manifesto para restore", e);
        }
    }

    private Snapshot findOwned(UUID userId, UUID snapshotId) {
        return snapshots.findByIdAndDeviceUserId(snapshotId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Snapshot não encontrado"));
    }

    private SnapshotDtos.SnapshotResponse toResponse(Snapshot s) {
        UUID deviceId = null;
        if (s.device != null) {
            // Se for proxy e tivermos só o ID na memória, Hibernate consegue pegar sem bater no banco dependendo do setup,
            // Mas para evitar NullPointer se o objeto foi detachado incorretamente:
            try {
                deviceId = s.device.id;
            } catch (Exception ignored) {}
        }
        
        return new SnapshotDtos.SnapshotResponse(
                s.id,
                deviceId,
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
