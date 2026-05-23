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
import java.util.Collections;
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

            Set<String> locallyKnown = db.getKnownChunks();
            Set<String> verifiedRemote = ConcurrentHashMap.newKeySet();
            
            if (!locallyKnown.isEmpty()) {
                log.accept("Verificando integridade de " + locallyKnown.size() + " chunks no servidor...");
                List<String> hashes = new ArrayList<>(locallyKnown);
                // Dividir em lotes de 1000 para evitar requests gigantes
                for (int i = 0; i < hashes.size(); i += 1000) {
                    List<String> batch = hashes.subList(i, Math.min(i + 1000, hashes.size()));
                    Set<String> existing = backend.checkChunks(batch);
                    verifiedRemote.addAll(existing);
                }
                log.accept("Integridade confirmada: " + verifiedRemote.size() + "/" + locallyKnown.size() + " chunks válidos.");
                
                // Limpar do banco local o que não existe mais no servidor
                locallyKnown.removeAll(verifiedRemote);
                if (!locallyKnown.isEmpty()) {
                    log.accept("⚠️ Removendo " + locallyKnown.size() + " chunks órfãos do cache local.");
                    db.removeKnownChunks(locallyKnown);
                }
            }

            List<FileManifest> manifestFiles = new ArrayList<>();
            Set<String> knownInSession = ConcurrentHashMap.newKeySet();
            knownInSession.addAll(verifiedRemote);
            
            AtomicInteger sentCount = new AtomicInteger(0);
            AtomicInteger totalFiles = new AtomicInteger(0);
            AtomicInteger filesCached = new AtomicInteger(0);
            AtomicInteger chunksGenerated = new AtomicInteger(0);
            AtomicInteger chunksReused = new AtomicInteger(0);
            AtomicLong totalOriginalSize = new AtomicLong(0);
            List<CompletableFuture<Void>> uploadFutures = new ArrayList<>();
            ContentDefinedChunker chunker = new ContentDefinedChunker();
            java.util.concurrent.Semaphore inFlightLimiter = new java.util.concurrent.Semaphore(8);

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
                        
                        boolean cacheValid = cached != null && cached.size() == size && cached.lastModified() == mtime;
                        if (cacheValid) {
                            for (ManifestChunk c : cached.chunks()) {
                                if (!knownInSession.contains(c.hash())) {
                                    cacheValid = false;
                                    break;
                                }
                            }
                        }

                        if (cacheValid) {
                            filesCached.incrementAndGet();
                            chunksReused.addAndGet(cached.chunks().size());
                            manifestFiles.add(new FileManifest(relativePath, size, Instant.ofEpochMilli(mtime), cached.hash(), cached.chunks()));
                        } else {
                            List<ManifestChunk> fileChunks = Collections.synchronizedList(new ArrayList<>());
                            
                            String fileHash = chunker.process(file, chunkData -> {
                                byte[] raw = chunkData.data();
                                String chunkHash = Sha256Hasher.hashBytes(raw);
                                
                                int originalSize = raw.length;
                                
                                if (knownInSession.add(chunkHash)) {
                                    byte[] compressed = GzipCompressor.compress(raw);
                                    ChunkPayload payload = new ChunkPayload(chunkHash, originalSize, compressed.length, compressed);
                                    
                                    inFlightLimiter.acquire();
                                    uploadFutures.add(CompletableFuture.runAsync(() -> {
                                        try {
                                            backend.uploadChunk(payload);
                                            db.addKnownChunks(Set.of(chunkHash));
                                            sentCount.incrementAndGet();
                                        } finally {
                                            inFlightLimiter.release();
                                        }
                                    }, executor));
                                    
                                    fileChunks.add(new ManifestChunk(chunkData.index(), chunkHash, originalSize, compressed.length));
                                } else {
                                    chunksReused.incrementAndGet();
                                    byte[] compressed = GzipCompressor.compress(raw);
                                    fileChunks.add(new ManifestChunk(chunkData.index(), chunkHash, originalSize, compressed.length));
                                }
                            });

                            fileChunks.sort(java.util.Comparator.comparingInt(ManifestChunk::index));
                            
                            FileManifest fm = new FileManifest(relativePath, size, Instant.ofEpochMilli(mtime), fileHash, fileChunks);
                            manifestFiles.add(fm);
                            db.saveFileCache(relativePath, size, mtime, fileHash, fileChunks);
                            chunksGenerated.addAndGet(fileChunks.size());
                        }
                    } catch (Exception e) {
                        // Mascara o caminho completo no log de erro também
                        String errorFile = sourceRoot.relativize(file).toString();
                        log.accept("Erro processando " + errorFile + ": " + e.getMessage());
                        throw new RuntimeException(e);
                    }
                });
            }
            double processDuration = (System.nanoTime() - startProcessing) / 1_000_000_000.0;
            
            if (!uploadFutures.isEmpty()) {
                log.accept("Aguardando conclusão de " + uploadFutures.size() + " uploads...");
                CompletableFuture.allOf(uploadFutures.toArray(new CompletableFuture[0])).join();
            }

            log.accept(String.format("[PERF] files.total=%d files.cached=%d files.changed=%d",
                    totalFiles.get(), filesCached.get(), totalFiles.get() - filesCached.get()));
            log.accept(String.format("[PERF] chunks.generated=%d chunks.reused=%d chunks.uploaded=%d",
                    chunksGenerated.get(), chunksReused.get(), sentCount.get()));
            log.accept(String.format("[PERF] time.processing=%.2fs", processDuration));

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
