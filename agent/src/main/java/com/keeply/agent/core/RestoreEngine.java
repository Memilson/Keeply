package com.keeply.agent.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.keeply.agent.api.BackendClient;
import com.keeply.agent.model.FileManifest;
import com.keeply.agent.model.ManifestChunk;
import com.keeply.agent.model.SnapshotManifest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class RestoreEngine {
    private static final Logger log = LoggerFactory.getLogger(RestoreEngine.class);
    private final BackendClient backend;
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    public RestoreEngine(BackendClient backend) {
        this.backend = backend;
    }

    public void restore(UUID snapshotId, Path destinationRoot) {
        restore(snapshotId, destinationRoot, null, OverwritePolicy.ALWAYS);
    }

    public void restore(UUID snapshotId, Path destinationRoot, Set<String> selectedPaths, OverwritePolicy overwritePolicy) {
        long startTotal = System.nanoTime();
        AtomicInteger totalFiles = new AtomicInteger(0);
        AtomicInteger skippedFiles = new AtomicInteger(0);
        AtomicLong totalRestoredSize = new AtomicLong(0);

        try {
            log.info("📥 Iniciando restauração do snapshot: {}", snapshotId);
            String json = backend.downloadManifest(snapshotId);
            SnapshotManifest manifest = mapper.readValue(json, SnapshotManifest.class);
            Set<String> selected = selectedPaths == null ? null : new HashSet<>(selectedPaths);
            Path originalRoot = Path.of(manifest.sourcePath());

            for (FileManifest file : manifest.files()) {
                if (selected != null && !selected.contains(file.path())) {
                    continue;
                }
                totalFiles.incrementAndGet();
                Path target;
                if (selected != null && destinationRoot != null) {
                    target = safeResolve(destinationRoot, Path.of(file.path()).getFileName().toString());
                } else {
                    target = destinationRoot == null
                            ? safeResolveOriginalRoot(originalRoot, file.path())
                            : safeResolve(destinationRoot, file.path());
                }

                if (!shouldRestore(target, file.lastModified(), overwritePolicy)) {
                    log.debug("📄 Ignorado pela política ({}): {}", overwritePolicy.label, file.path());
                    skippedFiles.incrementAndGet();
                    continue;
                }

                log.info("📄 Restaurando: {}", file.path());
                Files.createDirectories(target.getParent());

                try (OutputStream out = Files.newOutputStream(target)) {
                    for (ManifestChunk chunk : file.chunks()) {
                        byte[] gzip = backend.downloadChunk(chunk.hash());
                        byte[] original = GzipCompressor.decompress(gzip);

                        String hash = Sha256Hasher.hashBytes(original);
                        if (!hash.equals(chunk.hash())) {
                            throw new IllegalStateException("Hash inválido no chunk " + chunk.hash());
                        }

                        out.write(original);
                        totalRestoredSize.addAndGet(original.length);
                    }
                }

                String finalHash = Sha256Hasher.hashFile(target);
                if (!finalHash.equals(file.sha256())) {
                    throw new IllegalStateException("Hash final inválido no arquivo " + file.path());
                }

                Files.setLastModifiedTime(target, FileTime.from(file.lastModified()));
            }

            double totalDuration = (System.nanoTime() - startTotal) / 1_000_000_000.0;
            double throughput = (totalRestoredSize.get() / 1024.0 / 1024.0) / totalDuration;

            log.info("📊 [PERF] files.total={} files.skipped={} size.restored={}MB", 
                    totalFiles.get(), skippedFiles.get(), totalRestoredSize.get() / 1024 / 1024);
            log.info("📊 [PERF] total.duration={.2f}s throughput={.2f}MB/s", totalDuration, throughput);
            log.info("✅ Restore concluído com integridade validada.");
        } catch (Exception e) {
            log.error("❌ Restore falhou: {}", e.getMessage(), e);
            throw new IllegalStateException("Restore falhou", e);
        }
    }

    private Path safeResolve(Path root, String relativePath) {
        try {
            if (relativePath.startsWith("/") || relativePath.contains("..")) {
                throw new IllegalArgumentException("Caminho inválido no manifesto: " + relativePath);
            }

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
            if (relativePath.startsWith("/") || relativePath.contains("..")) {
                throw new IllegalArgumentException("Caminho inválido no manifesto: " + relativePath);
            }
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
}
