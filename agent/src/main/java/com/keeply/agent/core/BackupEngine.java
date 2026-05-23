package com.keeply.agent.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.keeply.agent.api.BackendClient;
import com.keeply.agent.model.ChunkPayload;
import com.keeply.agent.model.FileManifest;
import com.keeply.agent.model.ManifestChunk;
import com.keeply.agent.model.SnapshotManifest;
import com.keeply.agent.model.SnapshotSummary;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

public class BackupEngine {
    private final BackendClient backend;
    private final LocalDatabase db;
    private final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .findAndRegisterModules();

    public BackupEngine(BackendClient backend, LocalDatabase db) {
        this.backend = backend;
        this.db = db;
    }

    public UUID backup(UUID deviceId, Path sourceRoot, Consumer<String> log) {
        long startTotal = System.nanoTime();
        autoSyncCache(deviceId, sourceRoot, log);

        UUID snapshotId = backend.startSnapshot(deviceId, sourceRoot.toAbsolutePath().toString());

        try (ExecutorService executor = Executors.newFixedThreadPool(4)) {
            log.accept("Snapshot iniciado: " + snapshotId);

            List<FileManifest> manifestFiles = new ArrayList<>();
            Set<String> knownLocally = ConcurrentHashMap.newKeySet();
            knownLocally.addAll(db.getKnownChunks());
            
            AtomicInteger sentCount = new AtomicInteger(0);
            AtomicInteger totalFiles = new AtomicInteger(0);
            AtomicInteger filesCached = new AtomicInteger(0);
            AtomicLong totalOriginalSize = new AtomicLong(0);
            List<CompletableFuture<Void>> uploadFutures = new ArrayList<>();
            ContentDefinedChunker chunker = new ContentDefinedChunker();

            long startProcessing = System.nanoTime();
            try (var stream = FileScanner.scan(sourceRoot)) {
                stream.forEach(file -> {
                    try {
                        int currentTotal = totalFiles.incrementAndGet();
                        if (currentTotal % 1000 == 0) {
                            log.accept(String.format("Progresso: %d arquivos escaneados...", currentTotal));
                        }

                        Path relative = sourceRoot.relativize(file);
                        String relativePath = relative.toString().replace("\\", "/");
                        long size = Files.size(file);
                        long mtime = Files.getLastModifiedTime(file).toMillis();
                        totalOriginalSize.addAndGet(size);

                        LocalDatabase.CachedFile cached = db.getFileCache(relativePath);
                        
                        // SÓ usamos o cache se TODOS os chunks dele estiverem conhecidos localmente
                        boolean cacheValid = cached != null && cached.size() == size && cached.lastModified() == mtime;
                        if (cacheValid) {
                            for (ManifestChunk c : cached.chunks()) {
                                if (!knownLocally.contains(c.hash())) {
                                    cacheValid = false;
                                    break;
                                }
                            }
                        }

                        if (cacheValid) {
                            filesCached.incrementAndGet();
                            manifestFiles.add(new FileManifest(relativePath, size, Instant.ofEpochMilli(mtime), cached.hash(), cached.chunks()));
                        } else {
                            var chunkResult = chunker.chunk(file);
                            manifestFiles.add(new FileManifest(relativePath, size, Instant.ofEpochMilli(mtime), chunkResult.fileHash(), chunkResult.manifestChunks()));
                            db.saveFileCache(relativePath, size, mtime, chunkResult.fileHash(), chunkResult.manifestChunks());

                            for (ChunkPayload payload : chunkResult.payloads()) {
                                if (knownLocally.add(payload.hash())) {
                                    uploadFutures.add(CompletableFuture.runAsync(() -> {
                                        backend.uploadChunk(payload);
                                        db.addKnownChunks(Set.of(payload.hash()));
                                        sentCount.incrementAndGet();
                                    }, executor));
                                }
                            }
                        }
                    } catch (Exception e) {
                        log.accept("Erro processando " + file + ": " + e.getMessage());
                    }
                });
            }
            double processDuration = (System.nanoTime() - startProcessing) / 1_000_000_000.0;
            log.accept(String.format("[PERF] processing.files=%d cached=%d changed=%d duration=%.2fs",
                    totalFiles.get(), filesCached.get(), totalFiles.get() - filesCached.get(), processDuration));

            if (!uploadFutures.isEmpty()) {
                log.accept("Aguardando conclusão de " + uploadFutures.size() + " uploads...");
                CompletableFuture.allOf(uploadFutures.toArray(new CompletableFuture[0])).join();
            }

            long startManifest = System.nanoTime();
            SnapshotManifest manifest = new SnapshotManifest(
                    snapshotId.toString(),
                    sourceRoot.toAbsolutePath().toString(),
                    Instant.now(),
                    "CONTENT_DEFINED_MIN_512KB_AVG_1MB_MAX_4MB",
                    "GZIP",
                    "SHA-256",
                    manifestFiles
            );

            String manifestJson = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(manifest);
            
            // Cálculo do tamanho comprimido total (apenas chunks únicos no manifesto)
            Set<String> uniqueHashes = new java.util.HashSet<>();
            long totalCompressedSize = 0;
            for (FileManifest f : manifestFiles) {
                for (ManifestChunk c : f.chunks()) {
                    if (uniqueHashes.add(c.hash())) {
                        totalCompressedSize += c.compressedSize();
                    }
                }
            }

            backend.completeSnapshot(
                    snapshotId,
                    manifestJson,
                    totalFiles.get(),
                    totalOriginalSize.get(),
                    totalCompressedSize
            );
            double manifestDuration = (System.nanoTime() - startManifest) / 1_000_000_000.0;
            log.accept(String.format("[PERF] manifest.files=%d duration=%.2fs", totalFiles.get(), manifestDuration));

            db.setLastSyncedSnapshot(deviceId, sourceRoot.toAbsolutePath().toString(), snapshotId.toString());
            double totalDuration = (System.nanoTime() - startTotal) / 1_000_000_000.0;
            log.accept(String.format("[PERF] total.duration=%.2fs chunks.sent=%d", totalDuration, sentCount.get()));

            log.accept("Backup concluído com sucesso.");
            return snapshotId;
        } catch (Exception e) {
            backend.failSnapshot(snapshotId, e.getMessage());
            throw new IllegalStateException("Backup falhou", e);
        }
    }

    private void autoSyncCache(UUID deviceId, Path sourceRoot, Consumer<String> log) {
        try {
            String pathStr = sourceRoot.toAbsolutePath().toString();
            List<SnapshotSummary> snapshots = backend.listSnapshots();
            
            List<SnapshotSummary> sourceSnapshots = snapshots.stream()
                    .filter(s -> s.deviceId().equals(deviceId))
                    .filter(s -> s.sourcePath().equals(pathStr))
                    .filter(s -> "COMPLETED".equals(s.status()))
                    .toList();

            if (sourceSnapshots.isEmpty()) {
                // Se o servidor não tem nenhum snapshot para esta origem, mas o banco local tem cache,
                // significa que o servidor foi resetado. Limpamos o cache local para evitar "ghost chunks".
                log.accept("ℹ️ Servidor sem histórico para esta origem. Limpando cache local para sincronização total.");
                db.clearCacheForPath(pathStr);
                return;
            }

            SnapshotSummary latest = sourceSnapshots.get(0);
            String lastSynced = db.getLastSyncedSnapshot(deviceId, pathStr);
            if (!latest.id().toString().equals(lastSynced)) {
                log.accept("☁️ Nova versão de backup detectada na nuvem. Sincronizando memória local...");
                String json = backend.downloadManifest(latest.id());
                com.keeply.agent.model.SnapshotManifest manifest = mapper.readValue(json, com.keeply.agent.model.SnapshotManifest.class);
                db.reconstructIndex(manifest);
                db.setLastSyncedSnapshot(deviceId, pathStr, latest.id().toString());
                log.accept("✅ Memória local atualizada.");
            }
        } catch (Exception e) {
            log.accept("⚠️ Aviso: Não foi possível sincronizar o cache da nuvem automaticamente: " + e.getMessage());
        }
    }
}
