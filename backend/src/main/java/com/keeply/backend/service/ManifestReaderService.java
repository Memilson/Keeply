package com.keeply.backend.service;

import com.keeply.backend.dto.SnapshotDtos;
import com.keeply.backend.model.Snapshot;
import com.keeply.backend.model.SnapshotFile;
import com.keeply.backend.model.SnapshotStatus;
import com.keeply.backend.repository.SnapshotFileRepository;
import com.keeply.backend.repository.SnapshotRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.util.UUID;

@Service
public class ManifestReaderService {
    private final SnapshotRepository snapshots;
    private final SnapshotFileRepository snapshotFiles;

    public ManifestReaderService(SnapshotRepository snapshots, SnapshotFileRepository snapshotFiles) {
        this.snapshots = snapshots;
        this.snapshotFiles = snapshotFiles;
    }

    @Transactional(readOnly = true)
    public SnapshotDtos.SnapshotFileListResponse listFiles(UUID userId, UUID snapshotId, int page, int size, String search) {
        Snapshot snapshot = snapshots.findByIdAndDeviceUserId(snapshotId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Snapshot não encontrado"));

        if (snapshot.status != SnapshotStatus.COMPLETED) {
            throw new IllegalStateException("Snapshot ainda não concluído (Status: " + snapshot.status + ")");
        }

        PageRequest pageable = PageRequest.of(Math.max(page, 0), size, Sort.by("path").ascending());
        Page<SnapshotFile> result = (search == null || search.isBlank())
                ? snapshotFiles.findBySnapshotId(snapshotId, pageable)
                : snapshotFiles.findBySnapshotIdAndPathContainingIgnoreCase(snapshotId, search, pageable);

        var items = result.getContent().stream()
                .map(f -> new SnapshotDtos.SnapshotFileItem(
                        f.path,
                        f.size,
                        f.lastModified
                ))
                .toList();

        return new SnapshotDtos.SnapshotFileListResponse(
                items,
                new SnapshotDtos.PageMetadata(result.getTotalElements(), page, size)
        );
    }
}
