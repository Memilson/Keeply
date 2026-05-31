package com.keeply.agent.core;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.luben.zstd.ZstdInputStream;
import com.keeply.agent.api.BackendClient;
import com.keeply.agent.model.ChunkCompression;
import com.keeply.agent.model.FileManifest;
import com.keeply.agent.model.ManifestChunk;
import com.keeply.agent.model.TransferCredentials;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.OutputStream;
import java.io.InputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.HexFormat;

public class RestoreEngine {
    private static final Logger log = LoggerFactory.getLogger(RestoreEngine.class);
    private final BackendClient backend;
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final StorageFactory storageFactory;

    public RestoreEngine(BackendClient backend) {
        this(backend, DirectTransferStorage::new);
    }

    RestoreEngine(BackendClient backend, StorageFactory storageFactory) {
        this.backend = backend;
        this.storageFactory = storageFactory;
    }

    public void restore(UUID snapshotId, Path destinationRoot) {
        restore(snapshotId, destinationRoot, null, OverwritePolicy.ALWAYS);
    }

    public void restore(UUID snapshotId, Path destinationRoot, Set<String> selectedPaths, OverwritePolicy overwritePolicy) {
        long startTotal = System.nanoTime();
        AtomicInteger totalFiles = new AtomicInteger(0);
        AtomicInteger skippedFiles = new AtomicInteger(0);
        AtomicLong totalRestoredSize = new AtomicLong(0);

        TransferCredentials credentials = null;
        try {
            log.info("Iniciando restauracao do snapshot: {}", snapshotId);
            Set<String> selected = selectedPaths == null ? null : new HashSet<>(selectedPaths);
            credentials = backend.startRestoreSession(snapshotId);
            TransferObjectClient storage = storageFactory.create(backend, credentials);
            CompressionService compression = new CompressionService();
            ChunkCodec chunkCodec = null;
            Integer manifestVersion = null;
            try (var compressedManifest = storage.openManifest(snapshotId);
                 var stream = new ZstdInputStream(compressedManifest);
                 JsonParser parser = mapper.getFactory().createParser(stream)) {
                Path originalRoot = null;
                while (parser.nextToken() != null) {
                    if (parser.currentToken() == JsonToken.FIELD_NAME && "manifestVersion".equals(parser.currentName())) {
                        parser.nextToken();
                        manifestVersion = parser.getIntValue();
                        continue;
                    }
                    if (parser.currentToken() == JsonToken.FIELD_NAME && "sourcePath".equals(parser.currentName())) {
                        parser.nextToken();
                        originalRoot = Path.of(parser.getValueAsString());
                        continue;
                    }
                    if (parser.currentToken() == JsonToken.FIELD_NAME && "chunkCompression".equals(parser.currentName())) {
                        parser.nextToken();
                        ChunkCompression chunkCompression = mapper.readValue(parser, ChunkCompression.class);
                        requireManifestCompression(chunkCompression);
                        chunkCodec = compression.chunkCodec(chunkCompression.algorithm());
                        continue;
                    }
                    if (parser.currentToken() != JsonToken.FIELD_NAME || !"files".equals(parser.currentName())) continue;
                    requireManifestVersion(manifestVersion);
                    if (chunkCodec == null) {
                        throw new IllegalStateException("Manifesto v2 deve declarar chunkCompression");
                    }
                    parser.nextToken();
                    while (parser.nextToken() != JsonToken.END_ARRAY) {
                        FileManifest file = mapper.readValue(parser, FileManifest.class);
                        restoreFile(storage, chunkCodec, file, originalRoot, destinationRoot, selected, overwritePolicy,
                                totalFiles, skippedFiles, totalRestoredSize);
                    }
                }
            }

            double totalDuration = (System.nanoTime() - startTotal) / 1_000_000_000.0;
            double throughput = (totalRestoredSize.get() / 1024.0 / 1024.0) / totalDuration;

            log.info("[PERF] files.total={} files.skipped={} size.restored={}MB",
                    totalFiles.get(), skippedFiles.get(), totalRestoredSize.get() / 1024 / 1024);
            log.info("[PERF] total.duration={}s throughput={}MB/s",
                    String.format(java.util.Locale.ROOT, "%.2f", totalDuration),
                    String.format(java.util.Locale.ROOT, "%.2f", throughput));
            log.info("Restore concluido com integridade validada.");
        } catch (Exception e) {
            log.error("Restore falhou: {}", e.getMessage(), e);
            throw new IllegalStateException("Restore falhou", e);
        } finally {
            if (credentials != null) {
                try {
                    backend.finishTransferSession(credentials.transferSessionId());
                } catch (Exception e) {
                    log.warn("event=restore.transfer_session status=finish_failed message={}", e.getMessage());
                }
            }
        }
    }

    private void requireManifestVersion(Integer manifestVersion) {
        if (manifestVersion == null || manifestVersion != 2) {
            throw new IllegalStateException("Restore exige manifesto v2");
        }
    }

    private void requireManifestCompression(ChunkCompression compression) {
        if (compression == null || !"ZSTD".equalsIgnoreCase(compression.algorithm())
                || compression.level() == null || compression.level() != 3) {
            throw new IllegalStateException("Restore exige chunks ZSTD level 3");
        }
    }

    private void restoreFile(TransferObjectClient storage, ChunkCodec chunkCodec, FileManifest file, Path originalRoot, Path destinationRoot, Set<String> selected,
                             OverwritePolicy overwritePolicy, AtomicInteger totalFiles,
                             AtomicInteger skippedFiles, AtomicLong totalRestoredSize) throws Exception {
                if (selected != null && !selected.contains(file.path())) {
                    return;
                }
                totalFiles.incrementAndGet();
                Path target = destinationRoot == null
                        ? safeResolveOriginalRoot(originalRoot, file.path())
                        : safeResolve(destinationRoot, file.path());

                if (!shouldRestore(target, file.lastModified(), overwritePolicy)) {
                    log.debug("Ignorado pela politica ({}): {}", overwritePolicy.label, file.path());
                    skippedFiles.incrementAndGet();
                    return;
                }

                log.info("Restaurando: {}", file.path());
                Files.createDirectories(target.getParent());

                Path tempTarget = Files.createTempFile(target.getParent(), target.getFileName().toString(), ".restore.tmp");
                boolean completed = false;
                try (OutputStream out = Files.newOutputStream(tempTarget)) {
                    for (ManifestChunk chunk : file.chunks()) {
                        MessageDigest digest = MessageDigest.getInstance("SHA-256");
                        long size;
                        try (InputStream compressed = storage.openChunk(chunk.hash(), chunkCodec);
                             InputStream original = chunkCodec.openDecompressing(compressed);
                             DigestInputStream validated = new DigestInputStream(original, digest)) {
                            size = validated.transferTo(out);
                        }
                        String hash = HexFormat.of().formatHex(digest.digest());
                        if (!hash.equals(chunk.hash()) || size != chunk.originalSize()) {
                            throw new IllegalStateException("Hash inválido no chunk " + chunk.hash());
                        }
                        totalRestoredSize.addAndGet(size);
                    }

                    String finalHash = Sha256Hasher.hashFile(tempTarget);
                    if (!finalHash.equals(file.sha256())) {
                        throw new IllegalStateException("Hash final inválido no arquivo " + file.path());
                    }

                    Files.setLastModifiedTime(tempTarget, FileTime.from(file.lastModified()));
                    try {
                        Files.move(tempTarget, target,
                                java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                                java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                        Files.move(tempTarget, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    }
                    completed = true;
                } finally {
                    if (!completed) {
                        Files.deleteIfExists(tempTarget);
                    }
                }
    }

    private Path safeResolve(Path root, String relativePath) {
        try {
            validateRelativeManifestPath(relativePath);

            Path normalizedRoot = root.toAbsolutePath().normalize();
            Path target = normalizedRoot.resolve(relativePath).normalize();

            if (!target.startsWith(normalizedRoot)) {
                throw new IllegalArgumentException("Path traversal bloqueado: " + relativePath);
            }

            return target;
        } catch (Exception e) {
            throw new IllegalArgumentException("Caminho inseguro no restore: " + relativePath, e);
        }
    }

    private Path safeResolveOriginalRoot(Path sourceRoot, String relativePath) {
        try {
            validateRelativeManifestPath(relativePath);
            Path normalizedRoot = sourceRoot.toAbsolutePath().normalize();
            Path target = normalizedRoot.resolve(relativePath).normalize();
            if (!target.startsWith(normalizedRoot)) {
                throw new IllegalArgumentException("Path traversal bloqueado: " + relativePath);
            }
            return target;
        } catch (Exception e) {
            throw new IllegalArgumentException("Caminho inseguro no restore: " + relativePath, e);
        }
    }

    private void validateRelativeManifestPath(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            throw new IllegalArgumentException("Caminho vazio no manifesto");
        }
        Path path = Path.of(relativePath);
        if (path.isAbsolute()) {
            throw new IllegalArgumentException("Caminho absoluto no manifesto: " + relativePath);
        }
        for (Path component : path) {
            if ("..".equals(component.toString())) {
                throw new IllegalArgumentException("Path traversal bloqueado: " + relativePath);
            }
        }
    }

    private boolean shouldRestore(Path target, java.time.Instant backupLastModified, OverwritePolicy overwritePolicy) {
        try {
            if (!Files.exists(target)) return true;
            return switch (overwritePolicy) {
                case ALWAYS -> true;
                case SKIP_EXISTING -> false;
                case OVERWRITE_IF_BACKUP_NEWER -> backupLastModified.isAfter(Files.getLastModifiedTime(target).toInstant());
                case OVERWRITE_IF_BACKUP_OLDER -> backupLastModified.isBefore(Files.getLastModifiedTime(target).toInstant());
            };
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao avaliar política de sobrescrita para: " + target, e);
        }
    }

    public enum OverwritePolicy {
        ALWAYS("Sempre sobrescrever"),
        SKIP_EXISTING("Não sobrescrever existentes"),
        OVERWRITE_IF_BACKUP_NEWER("Sobrescrever se backup for mais novo"),
        OVERWRITE_IF_BACKUP_OLDER("Sobrescrever se backup for mais antigo");

        public final String label;

        OverwritePolicy(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    @FunctionalInterface
    interface StorageFactory {
        TransferObjectClient create(BackendClient backend, TransferCredentials credentials);
    }
}
