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
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
@Transactional(readOnly = true)
public class FileDownloadService {
    // VULN-015: limite de 5 GB para download de archive (evita downloads sem limite operacional)
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
        List<ArchiveFile> files = paths.stream()
                .flatMap(path -> resolveArchiveFiles(snapshotId, path).stream())
                .toList();

        // VULN-015: verificar tamanho total antes de iniciar o stream
        long totalSize = files.stream().mapToLong(f -> f.file.size).sum();
        if (totalSize > MAX_ARCHIVE_TOTAL_BYTES) {
            throw new IllegalArgumentException(
                    "Tamanho total dos arquivos selecionados excede o limite de 5 GB para download"
            );
        }

        response.setContentType("application/zip");
        response.setHeader("Content-Disposition", "attachment; filename=\"keeply-selected-" + snapshotId + ".zip\"");

        try (ZipOutputStream zip = new ZipOutputStream(response.getOutputStream())) {
            Map<String, Integer> usedEntryNames = new HashMap<>();
            for (ArchiveFile archiveFile : files) {
                ZipEntry entry = new ZipEntry(uniqueEntryName(archiveFile.entryName, usedEntryNames));
                if (archiveFile.file.lastModified != null) {
                    entry.setTime(archiveFile.file.lastModified.toEpochMilli());
                }
                zip.putNextEntry(entry);
                streamSnapshotFileContent(userId, archiveFile.file, zip);
                zip.closeEntry();
            }
            zip.finish();
        }
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

    private List<ArchiveFile> resolveArchiveFiles(UUID snapshotId, String path) {
        if (path.isBlank() || path.endsWith("/")) {
            List<SnapshotFile> folderFiles = snapshotFileRepository.findBySnapshotIdAndPathStartingWith(
                    snapshotId,
                    path,
                    org.springframework.data.domain.Sort.by("path").ascending()
            );
            if (folderFiles.isEmpty()) {
                throw new IllegalArgumentException(path.isBlank()
                        ? "Snapshot has no files"
                        : "Folder not found in snapshot: " + path);
            }
            return folderFiles.stream()
                    .map(file -> new ArchiveFile(file, relativeFolderEntryName(path, file.path)))
                    .toList();
        }

        SnapshotFile file = requireSnapshotFile(snapshotId, path);
        return List.of(new ArchiveFile(file, basename(file.path)));
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
                .filter(path -> path != null)
                .distinct()
                .toList();

        if (paths.isEmpty()) {
            throw new IllegalArgumentException("At least one file path must be provided");
        }
        return paths;
    }

    private String uniqueEntryName(String entryName, Map<String, Integer> usedEntryNames) {
        String safeEntryName = safeZipEntryName(entryName);
        int count = usedEntryNames.merge(safeEntryName, 1, Integer::sum);
        if (count == 1) {
            return safeEntryName;
        }

        int slash = safeEntryName.lastIndexOf('/');
        String dir = slash >= 0 ? safeEntryName.substring(0, slash + 1) : "";
        String name = slash >= 0 ? safeEntryName.substring(slash + 1) : safeEntryName;
        int dot = name.lastIndexOf('.');
        if (dot > 0) {
            return dir + name.substring(0, dot) + " (" + count + ")" + name.substring(dot);
        }
        return dir + name + " (" + count + ")";
    }

    private String relativeFolderEntryName(String folderPath, String filePath) {
        String relative = filePath.substring(Math.min(folderPath.length(), filePath.length()));
        return relative.isBlank() ? basename(filePath) : relative;
    }

    private String safeZipEntryName(String entryName) {
        String normalized = entryName.replace('\\', '/');
        if (normalized.startsWith("/") || normalized.contains("../") || normalized.startsWith("..")) {
            throw new IllegalArgumentException("Nome de arquivo inválido no snapshot: " + entryName);
        }
        return normalized;
    }

    private String basename(String snapshotPath) {
        String normalized = snapshotPath.replace('\\', '/');
        int slash = normalized.lastIndexOf('/');
        String name = slash >= 0 ? normalized.substring(slash + 1) : normalized;
        if (name.isBlank() || ".".equals(name) || "..".equals(name)) {
            throw new IllegalArgumentException("Nome de arquivo inválido no snapshot: " + snapshotPath);
        }
        return name;
    }

    private record ArchiveFile(SnapshotFile file, String entryName) {}
}
