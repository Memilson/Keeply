package com.keeply.backend.service;

import com.github.luben.zstd.ZstdInputStream;
import com.keeply.backend.model.FileChunk;
import com.keeply.backend.model.Snapshot;
import com.keeply.backend.model.SnapshotFile;
import com.keeply.backend.model.SnapshotStatus;
import com.keeply.backend.repository.FileChunkRepository;
import com.keeply.backend.repository.SnapshotFileRepository;
import com.keeply.backend.repository.SnapshotRepository;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class FileDownloadService {

    private final SnapshotRepository snapshotRepository;
    private final SnapshotFileRepository snapshotFileRepository;
    private final FileChunkRepository fileChunkRepository;
    private final ObjectStorageService objectStorage;

    public FileDownloadService(
            SnapshotRepository snapshotRepository,
            SnapshotFileRepository snapshotFileRepository,
            FileChunkRepository fileChunkRepository,
            ObjectStorageService objectStorage) {
        this.snapshotRepository = snapshotRepository;
        this.snapshotFileRepository = snapshotFileRepository;
        this.fileChunkRepository = fileChunkRepository;
        this.objectStorage = objectStorage;
    }

    public void streamFile(UUID userId, UUID snapshotId, String filePath, HttpServletResponse response) throws IOException {
        Snapshot snapshot = snapshotRepository.findByIdAndDeviceUserId(snapshotId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Snapshot not found: " + snapshotId));

        if (snapshot.status != SnapshotStatus.COMPLETED) {
            throw new IllegalStateException("Snapshot is not completed: " + snapshotId);
        }

        SnapshotFile file = snapshotFileRepository.findBySnapshotIdAndPath(snapshotId, filePath)
                .orElseThrow(() -> new IllegalArgumentException("File not found in snapshot: " + filePath));

        List<FileChunk> chunks = fileChunkRepository.findBySnapshotFileIdOrderByChunkIndexAsc(file.id);

        String basename = Paths.get(filePath).getFileName().toString();
        response.setContentType("application/octet-stream");
        response.setHeader("Content-Disposition", "attachment; filename=\"" + basename + "\"");
        response.setHeader("X-File-Size", String.valueOf(file.size));

        OutputStream out = response.getOutputStream();
        for (FileChunk chunk : chunks) {
            String key = ChunkService.chunkKey(userId, chunk.chunkHash);
            try (InputStream raw = objectStorage.getStream(key);
                 ZstdInputStream zstd = new ZstdInputStream(raw)) {
                zstd.transferTo(out);
            }
        }
    }
}
