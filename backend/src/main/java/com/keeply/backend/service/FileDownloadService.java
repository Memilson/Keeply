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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
@Transactional(readOnly = true)
public class FileDownloadService {
    private static final int MAX_SELECTED_FILES = 10;
    // VULN-015: limite de 5 GB para download de archive (evita exhaustão de banda/memória)
    private static final long MAX_ARCHIVE_TOTAL_BYTES = 5L * 1024 * 1024 * 1024;

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
        requireCompletedSnapshot(userId, snapshotId);
        String sanitized = sanitizeFilePath(filePath); // VULN-007: previne path traversal
        SnapshotFile file = requireSnapshotFile(snapshotId, sanitized);
        String basename = Paths.get(sanitized).getFileName().toString();

        response.setContentType("application/octet-stream");
        response.setHeader("Content-Disposition", "attachment; filename=\"" + basename + "\"");
        response.setHeader("X-File-Size", String.valueOf(file.size));

        streamSnapshotFileContent(userId, file, response.getOutputStream());
    }

    public void streamSelectedArchive(UUID userId, UUID snapshotId, List<String> requestedPaths, HttpServletResponse response) throws IOException {
        requireCompletedSnapshot(userId, snapshotId);
        List<String> paths = normalizeSelectedPaths(requestedPaths);
        List<SnapshotFile> files = paths.stream()
                .map(path -> requireSnapshotFile(snapshotId, path))
                .toList();

        // VULN-015: verificar tamanho total antes de iniciar o stream
        long totalSize = files.stream().mapToLong(f -> f.size).sum();
        if (totalSize > MAX_ARCHIVE_TOTAL_BYTES) {
            throw new IllegalArgumentException(
                    "Tamanho total dos arquivos selecionados excede o limite de 5 GB para download"
            );
        }

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(buffer)) {
            for (SnapshotFile file : files) {
                ZipEntry entry = new ZipEntry(file.path);
                if (file.lastModified != null) {
                    entry.setTime(file.lastModified.toEpochMilli());
                }
                zip.putNextEntry(entry);
                streamSnapshotFileContent(userId, file, zip);
                zip.closeEntry();
            }
            zip.finish();
        }

        response.setContentType("application/zip");
        response.setHeader("Content-Disposition", "attachment; filename=\"keeply-selected-" + snapshotId + ".zip\"");
        response.setContentLength(buffer.size());
        buffer.writeTo(response.getOutputStream());
    }

    private Snapshot requireCompletedSnapshot(UUID userId, UUID snapshotId) {
        Snapshot snapshot = snapshotRepository.findByIdAndDeviceUserId(snapshotId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Snapshot not found: " + snapshotId));

        if (snapshot.status != SnapshotStatus.COMPLETED) {
            throw new IllegalStateException("Snapshot is not completed: " + snapshotId);
        }

        return snapshot;
    }

    private SnapshotFile requireSnapshotFile(UUID snapshotId, String filePath) {
        return snapshotFileRepository.findBySnapshotIdAndPath(snapshotId, filePath)
                .orElseThrow(() -> new IllegalArgumentException("File not found in snapshot: " + filePath));
    }

    /**
     * VULN-007: Sanitiza o path recebido do usuário para prevenir path traversal.
     * Normaliza separadores e rejeita qualquer path que contenha componentes relativos
     * após normalização (ex: ../../outro-usuario/arquivo.txt).
     */
    private String sanitizeFilePath(String path) {
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("path é obrigatório");
        }
        try {
            // Normaliza separadores e resolve componentes ..
            String normalized = Path.of(path).normalize().toString();
            // Rejeitar qualquer path que ainda comece com .. após normalização
            if (normalized.startsWith("..") || normalized.contains("/../")) {
                throw new IllegalArgumentException("path inválido: tentativa de path traversal detectada");
            }
            // Rejeitar paths absolutos que possam tentar acessar o filesystem do servidor
            if (Path.of(normalized).isAbsolute() && !normalized.startsWith("/")) {
                throw new IllegalArgumentException("path inválido");
            }
            return normalized;
        } catch (InvalidPathException e) {
            throw new IllegalArgumentException("path inválido: " + e.getMessage());
        }
    }

    private void streamSnapshotFileContent(UUID userId, SnapshotFile file, OutputStream out) throws IOException {
        List<FileChunk> chunks = fileChunkRepository.findBySnapshotFileIdOrderByChunkIndexAsc(file.id);
        for (FileChunk chunk : chunks) {
            String key = ChunkService.chunkKey(userId, chunk.chunkHash);
            try (InputStream raw = objectStorage.getStream(key);
                 ZstdInputStream zstd = new ZstdInputStream(raw)) {
                zstd.transferTo(out);
            }
        }
    }

    private List<String> normalizeSelectedPaths(List<String> requestedPaths) {
        if (requestedPaths == null || requestedPaths.isEmpty()) {
            throw new IllegalArgumentException("At least one file path must be provided");
        }

        List<String> paths = requestedPaths.stream()
                .map(path -> path == null ? null : path.trim())
                .filter(path -> path != null && !path.isEmpty())
                .distinct()
                .toList();

        if (paths.isEmpty()) {
            throw new IllegalArgumentException("At least one file path must be provided");
        }
        if (paths.size() > MAX_SELECTED_FILES) {
            throw new IllegalArgumentException("A maximum of 10 files can be downloaded per archive");
        }
        return paths;
    }
}
