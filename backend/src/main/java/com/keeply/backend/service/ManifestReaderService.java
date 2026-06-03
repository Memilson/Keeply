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
        return listFiles(userId, snapshotId, page, size, search, null);
    }

    @Transactional(readOnly = true)
    public SnapshotDtos.SnapshotFileListResponse listFiles(UUID userId, UUID snapshotId, int page, int size, String search, String prefix) {
        Snapshot snapshot = snapshots.findByIdAndDeviceUserId(snapshotId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Snapshot não encontrado"));

        if (snapshot.status != SnapshotStatus.COMPLETED) {
            throw new IllegalStateException("Snapshot ainda não concluído (Status: " + snapshot.status + ")");
        }

        // VULN-010: limites aplicados internamente para proteger todos os call-sites
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 200);
        PageRequest pageable = PageRequest.of(safePage, safeSize, Sort.by("path").ascending());
        Page<SnapshotFile> result;
        if (prefix != null && !prefix.isBlank()) {
            result = snapshotFiles.findBySnapshotIdAndPathStartingWith(snapshotId, prefix, pageable);
        } else if (search != null && !search.isBlank()) {
            result = snapshotFiles.findBySnapshotIdAndPathContainingIgnoreCase(snapshotId, search, pageable);
        } else {
            result = snapshotFiles.findBySnapshotId(snapshotId, pageable);
        }

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
