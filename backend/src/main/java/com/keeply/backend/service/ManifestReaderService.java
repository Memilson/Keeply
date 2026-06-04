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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
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

    @Transactional(readOnly = true)
    public SnapshotDtos.SnapshotNodeListResponse listNodes(UUID userId, UUID snapshotId, String prefix) {
        Snapshot snapshot = snapshots.findByIdAndDeviceUserId(snapshotId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Snapshot não encontrado"));

        if (snapshot.status != SnapshotStatus.COMPLETED) {
            throw new IllegalStateException("Snapshot ainda não concluído (Status: " + snapshot.status + ")");
        }

        String normalizedPrefix = prefix == null ? "" : prefix;
        Sort sort = Sort.by("path").ascending();
        List<SnapshotFile> files = normalizedPrefix.isBlank()
                ? snapshotFiles.findBySnapshotId(snapshotId, sort)
                : snapshotFiles.findBySnapshotIdAndPathStartingWith(snapshotId, normalizedPrefix, sort);

        LinkedHashMap<String, SnapshotDtos.SnapshotNodeItem> nodes = new LinkedHashMap<>();
        for (SnapshotFile file : files) {
            if (!file.path.startsWith(normalizedPrefix)) {
                continue;
            }
            String relative = normalizedPrefix.isBlank()
                    ? file.path
                    : file.path.substring(Math.min(normalizedPrefix.length(), file.path.length()));
            if (relative.isBlank()) {
                continue;
            }

            int slash = relative.indexOf('/');
            if (slash < 0) {
                nodes.putIfAbsent(file.path, new SnapshotDtos.SnapshotNodeItem(
                        relative,
                        file.path,
                        false,
                        file.size,
                        file.lastModified
                ));
                continue;
            }

            String folderName = relative.substring(0, slash);
            String folderPath = normalizedPrefix + folderName + "/";
            nodes.putIfAbsent(folderPath, new SnapshotDtos.SnapshotNodeItem(
                    folderName,
                    folderPath,
                    true,
                    null,
                    null
            ));
        }

        List<SnapshotDtos.SnapshotNodeItem> items = new ArrayList<>(nodes.values());
        items.sort((left, right) -> {
            if (left.directory() != right.directory()) {
                return left.directory() ? -1 : 1;
            }
            boolean leftHidden = left.name().startsWith(".");
            boolean rightHidden = right.name().startsWith(".");
            if (leftHidden != rightHidden) {
                return leftHidden ? 1 : -1;
            }
            return left.name().compareToIgnoreCase(right.name());
        });
        return new SnapshotDtos.SnapshotNodeListResponse(items);
    }
}
