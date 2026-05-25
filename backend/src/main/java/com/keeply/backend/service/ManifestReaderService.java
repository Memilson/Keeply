package com.keeply.backend.service;

import com.keeply.backend.dto.SnapshotDtos;
import com.keeply.backend.model.Snapshot;
import com.keeply.backend.model.SnapshotFile;
import com.keeply.backend.model.SnapshotStatus;
import com.keeply.backend.repository.SnapshotFileRepository;
import com.keeply.backend.repository.SnapshotRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
        Snapshot snapshot = snapshots.findByIdAndDeviceUserId(snapshotId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Snapshot não encontrado"));

        if (snapshot.status != SnapshotStatus.COMPLETED) {
            throw new IllegalStateException("Snapshot ainda não concluído (Status: " + snapshot.status + ")");
        }

        // Recupera todos os arquivos do snapshot (ordenados por path por padrão se necessário, ou podemos adicionar sorting no repo)
        // Nota: Para grandes volumes, o ideal seria implementar paginação diretamente no Repository (Pageable)
        List<SnapshotFile> allFiles = snapshotFiles.findBySnapshotId(snapshotId);

        // Filtragem (search)
        List<SnapshotFile> filtered = (search == null || search.isBlank())
                ? allFiles
                : allFiles.stream()
                .filter(f -> f.path.toLowerCase().contains(search.toLowerCase()))
                .toList();

        // Ordenação por caminho para consistência na paginação
        filtered = filtered.stream()
                .sorted((f1, f2) -> f1.path.compareToIgnoreCase(f2.path))
                .toList();

        // Paginação
        int totalElements = filtered.size();
        int start = Math.min(page * size, totalElements);
        int end = Math.min(start + size, totalElements);

        List<SnapshotDtos.SnapshotFileItem> items = filtered.subList(start, end).stream()
                .map(f -> new SnapshotDtos.SnapshotFileItem(
                        f.path,
                        f.size,
                        f.lastModified
                ))
                .toList();

        return new SnapshotDtos.SnapshotFileListResponse(
                items,
                new SnapshotDtos.PageMetadata(totalElements, page, size)
        );
    }
}
