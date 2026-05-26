package com.keeply.agent.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.keeply.agent.api.BackendClient;
import com.keeply.agent.model.ChunkPayload;
import com.keeply.agent.model.FileManifest;
import com.keeply.agent.model.ManifestChunk;
import com.keeply.agent.model.SnapshotManifest;
import com.keeply.agent.model.SnapshotSummary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class BackupEngine {
    private static final Logger log = LoggerFactory.getLogger(BackupEngine.class);
    private static final int CHUNK_UPLOAD_BATCH_SIZE = 100;
    private final BackendClient backend;
    private final LocalDatabase db;
    private final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .findAndRegisterModules();

    public BackupEngine(BackendClient backend, LocalDatabase db) {
        this.backend = backend;
        this.db = db;
    }

    public UUID backup(UUID deviceId, Path sourceRoot) {
        long startTotal = System.nanoTime();
        String sourcePath = sourceRoot.toAbsolutePath().normalize().toString();
        autoSyncCache(deviceId, sourceRoot);

        UUID snapshotId = backend.startSnapshot(deviceId, sourcePath);

        try {
            log.info("event=backup.snapshot status=started snapshot_id={} source_path={}", snapshotId, sourcePath);

            Set<String> locallyKnown = db.getKnownChunks();
            Set<String> verifiedRemote = ConcurrentHashMap.newKeySet();

            if (!locallyKnown.isEmpty()) {
                log.info("event=backup.chunk_integrity_check status=started total={}", locallyKnown.size());
                List<String> hashes = new ArrayList<>(locallyKnown);
                // Dividir em lotes de 1000 para evitar requests gigantes
                for (int i = 0; i < hashes.size(); i += 1000) {
                    List<String> batch = hashes.subList(i, Math.min(i + 1000, hashes.size()));
                    Set<String> existing = backend.checkChunks(batch);
                    verifiedRemote.addAll(existing);
                }
                log.info("event=backup.chunk_integrity_check status=completed valid={} total={}", verifiedRemote.size(), locallyKnown.size());
                
                // Limpar do banco local o que não existe mais no servidor
                locallyKnown.removeAll(verifiedRemote);
                if (!locallyKnown.isEmpty()) {
                    log.warn("event=backup.local_cache_cleanup orphan_chunks_removed={}", locallyKnown.size());
                    db.removeKnownChunks(locallyKnown);
                }
            }

            Set<String> knownInSession = ConcurrentHashMap.newKeySet();
            knownInSession.addAll(verifiedRemote);

            // Controle de hashes únicos para cálculo do tamanho comprimido total
            Set<String> uniqueHashesInManifest = ConcurrentHashMap.newKeySet();
            AtomicLong totalCompressedSize = new AtomicLong(0);

            AtomicInteger sentCount = new AtomicInteger(0);
            AtomicInteger duplicateCount = new AtomicInteger(0);
            AtomicInteger failedBatchItems = new AtomicInteger(0);
            AtomicInteger totalFiles = new AtomicInteger(0);
            AtomicInteger filesCached = new AtomicInteger(0);
            AtomicInteger chunksGenerated = new AtomicInteger(0);
            AtomicInteger chunksReused = new AtomicInteger(0);
            AtomicLong totalOriginalSize = new AtomicLong(0);
            ContentDefinedChunker chunker = new ContentDefinedChunker();

            // A fila do próprio executor também precisa ser limitada: cada tarefa retém um chunk bruto.
            java.util.concurrent.ThreadPoolExecutor uploaderPool = new java.util.concurrent.ThreadPoolExecutor(
                    4,
                    4,
                    0L,
                    java.util.concurrent.TimeUnit.MILLISECONDS,
                    new java.util.concurrent.ArrayBlockingQueue<>(4),
                    new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy()
            );
            java.util.concurrent.ConcurrentLinkedQueue<Exception> uploadErrors =
                    new java.util.concurrent.ConcurrentLinkedQueue<>();
            ChunkBatchUploader batchUploader = new ChunkBatchUploader(backend, db, sentCount, duplicateCount, failedBatchItems);

            db.clearBackupManifest();

            // Pré-scan rápido para contar arquivos e permitir porcentagem
            log.info("event=backup.scan status=started");
            long totalFilesToProcess;
            try (var preScanStream = FileScanner.scan(sourceRoot)) {
                totalFilesToProcess = preScanStream.count();
            }
            log.info("event=backup.scan status=completed files_total={}", totalFilesToProcess);

            AtomicInteger lastLoggedPercentage = new AtomicInteger(-1);
            long startProcessing = System.nanoTime();
            try (var stream = FileScanner.scan(sourceRoot)) {
                stream.forEach(file -> {
                    String relativePath = sourceRoot.relativize(file).toString().replace("\\", "/");
                    try {
                        int currentTotal = totalFiles.incrementAndGet();
                        if (totalFilesToProcess > 0) {
                            int percentage = (int) ((currentTotal * 100) / totalFilesToProcess);
                            if (percentage > lastLoggedPercentage.get()) {
                                lastLoggedPercentage.set(percentage);
                                log.info("event=backup.progress percent={}", percentage);
                            }
                        }

                        long size = Files.size(file);
                        long mtime = Files.getLastModifiedTime(file).toMillis();
                        totalOriginalSize.addAndGet(size);

                        LocalDatabase.CachedFile cached = db.getFileCache(sourcePath, relativePath);
                        
                        boolean cacheValid = cached != null && cached.size() == size && cached.lastModified() == mtime;
                        if (cacheValid) {
                            for (ManifestChunk c : cached.chunks()) {
                                if (!knownInSession.contains(c.hash())) {
                                    cacheValid = false;
                                    log.debug("📄 Cache inválido para {}: chunk {} não encontrado na sessão", relativePath, c.hash());
                                    break;
                                }
                            }
                        }

                        if (cacheValid) {
                            log.debug("📄 Cache hit: {}", relativePath);
                            filesCached.incrementAndGet();
                            chunksReused.addAndGet(cached.chunks().size());
                            db.addManifestFile(relativePath, size, mtime, cached.hash());
                            for (ManifestChunk c : cached.chunks()) {
                                db.addManifestChunk(relativePath, c.index(), c.hash(), c.originalSize(), c.compressedSize());
                                if (uniqueHashesInManifest.add(c.hash())) {
                                    totalCompressedSize.addAndGet(c.compressedSize());
                                }
                            }
                        } else {
                            log.debug("📄 Cache miss: {}", relativePath);
                            String fileHash = chunker.process(file, chunkData -> {
                                byte[] raw = chunkData.data();
                                String chunkHash = Sha256Hasher.hashBytes(raw);
                                int originalSize = raw.length;
                                
                                if (knownInSession.add(chunkHash)) {
                                    uploaderPool.execute(() -> {
                                        try {
                                            byte[] compressed = GzipCompressor.compress(raw);
                                            ChunkPayload payload = new ChunkPayload(chunkHash, originalSize, compressed.length, compressed);
                                            batchUploader.enqueue(payload);
                                            db.addManifestChunk(relativePath, chunkData.index(), chunkHash, originalSize, compressed.length);
                                            if (uniqueHashesInManifest.add(chunkHash)) {
                                                totalCompressedSize.addAndGet(compressed.length);
                                            }
                                        } catch (Exception e) {
                                            uploadErrors.add(e);
                                            log.debug("Erro no upload do chunk {}: {}", chunkHash, e.getMessage());
                                        }
                                    });
                                } else {
                                    chunksReused.incrementAndGet();
                                    byte[] compressed = GzipCompressor.compress(raw);
                                    db.addManifestChunk(relativePath, chunkData.index(), chunkHash, originalSize, compressed.length);
                                    if (uniqueHashesInManifest.add(chunkHash)) {
                                        totalCompressedSize.addAndGet(compressed.length);
                                    }
                                }
                            });

                            db.addManifestFile(relativePath, size, mtime, fileHash);
                            db.saveFileCache(sourcePath, relativePath, size, mtime, fileHash, null); 
                            chunksGenerated.incrementAndGet(); 
                        }
                    } catch (java.nio.file.NoSuchFileException e) {
                        log.warn("event=backup.file status=skipped reason=removed_during_scan path={}", relativePath);
                    } catch (Exception e) {
                        if (e.getCause() instanceof java.nio.file.NoSuchFileException) {
                            log.warn("event=backup.file status=skipped reason=removed_during_processing path={}", relativePath);
                        } else {
                            log.error("event=backup.file status=failed path={} message={}", relativePath, e.getMessage());
                        }
                    }
                });
            }
            
            // Finaliza o processamento em background
            uploaderPool.shutdown();
            while (!uploaderPool.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)) {
                log.info("event=backup.upload status=waiting_for_pending_workers");
            }
            batchUploader.flushRemaining();
            if (!uploadErrors.isEmpty()) {
                throw new IllegalStateException("Falha ao enviar um ou mais chunks", uploadErrors.peek());
            }

            double processDuration = (System.nanoTime() - startProcessing) / 1_000_000_000.0;
            double throughputOriginal = (totalOriginalSize.get() / 1024.0 / 1024.0) / processDuration;

            log.info("event=backup.summary files_total={} files_cached={} files_changed={}",
                    totalFiles.get(), filesCached.get(), totalFiles.get() - filesCached.get());
            log.info("event=backup.summary chunks_generated={} chunks_reused={} chunks_uploaded={}",
                    chunksGenerated.get(), chunksReused.get(), sentCount.get());
            log.info("event=backup.summary chunks_duplicate={} chunks_failed={}",
                    duplicateCount.get(), failedBatchItems.get());
            log.info("event=backup.summary size_original_mb={} size_compressed_mb={}",
                    totalOriginalSize.get() / 1024 / 1024, totalCompressedSize.get() / 1024 / 1024);
            log.info("event=backup.summary processing_seconds={} throughput_mb_s={}",
                    String.format(java.util.Locale.ROOT, "%.2f", processDuration),
                    String.format(java.util.Locale.ROOT, "%.2f", throughputOriginal));

            long startManifest = System.nanoTime();

            List<FileManifest> finalManifestFiles;
            try (var manifestStream = db.getManifestFilesStream()) {
                finalManifestFiles = manifestStream.toList();
            }

            SnapshotManifest manifest = new SnapshotManifest(
                    snapshotId.toString(),
                    sourcePath,
                    Instant.now(),
                    "CONTENT_DEFINED_MIN_512KB_AVG_1MB_MAX_4MB",
                    "GZIP",
                    "SHA-256",
                    finalManifestFiles
            );

            String manifestJson = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(manifest);

            backend.completeSnapshot(
                    snapshotId,
                    manifestJson,
                    totalFiles.get(),
                    totalOriginalSize.get(),
                    totalCompressedSize.get()
            );

            // Atualiza o cache de arquivos para o próximo backup incremental
            for (FileManifest f : finalManifestFiles) {
                db.saveFileCache(sourcePath, f.path(), f.size(), f.lastModified().toEpochMilli(), f.sha256(), f.chunks());
            }

            double manifestDuration = (System.nanoTime() - startManifest) / 1_000_000_000.0;
            log.info("event=backup.manifest status=completed files={} duration_seconds={}",
                    totalFiles.get(),
                    String.format(java.util.Locale.ROOT, "%.2f", manifestDuration));

            db.setLastSyncedSnapshot(deviceId, sourcePath, snapshotId.toString());
            db.clearBackupManifest(); 

            double totalDuration = (System.nanoTime() - startTotal) / 1_000_000_000.0;
            log.info("event=backup.snapshot status=completed snapshot_id={} total_duration_seconds={} chunks_sent={}",
                    snapshotId,
                    String.format(java.util.Locale.ROOT, "%.2f", totalDuration),
                    sentCount.get());

            return snapshotId;
        } catch (Exception e) {
            backend.failSnapshot(snapshotId, e.getMessage());
            throw new IllegalStateException("Backup falhou", e);
        }
    }

    private static final class ChunkBatchUploader {
        private final BackendClient backend;
        private final LocalDatabase db;
        private final AtomicInteger sentCount;
        private final AtomicInteger duplicateCount;
        private final AtomicInteger failedCount;
        private final List<ChunkPayload> pending = new ArrayList<>(CHUNK_UPLOAD_BATCH_SIZE);

        private ChunkBatchUploader(
                BackendClient backend,
                LocalDatabase db,
                AtomicInteger sentCount,
                AtomicInteger duplicateCount,
                AtomicInteger failedCount
        ) {
            this.backend = backend;
            this.db = db;
            this.sentCount = sentCount;
            this.duplicateCount = duplicateCount;
            this.failedCount = failedCount;
        }

        void enqueue(ChunkPayload payload) {
            List<ChunkPayload> toSend = null;
            synchronized (this) {
                pending.add(payload);
                if (pending.size() >= CHUNK_UPLOAD_BATCH_SIZE) {
                    toSend = new ArrayList<>(pending);
                    pending.clear();
                }
            }
            if (toSend != null) {
                sendBatch(toSend);
            }
        }

        void flushRemaining() {
            List<ChunkPayload> toSend;
            synchronized (this) {
                if (pending.isEmpty()) {
                    return;
                }
                toSend = new ArrayList<>(pending);
                pending.clear();
            }
            sendBatch(toSend);
        }

        private void sendBatch(List<ChunkPayload> batch) {
            long start = System.nanoTime();
            BackendClient.BatchUploadStats stats = backend.uploadChunksBatch(batch);
            sentCount.addAndGet(stats.stored());
            duplicateCount.addAndGet(stats.duplicate());
            failedCount.addAndGet(stats.failed());
            db.addKnownChunks(batch.stream().map(ChunkPayload::hash).collect(java.util.stream.Collectors.toSet()));
            long tookMs = (System.nanoTime() - start) / 1_000_000;
            log.info(
                    "event=chunk.upload.batch size={} sent={} stored={} duplicate={} failed={} latency_ms={}",
                    batch.size(),
                    stats.sent(),
                    stats.stored(),
                    stats.duplicate(),
                    stats.failed(),
                    tookMs
            );
        }
    }

    private void autoSyncCache(UUID deviceId, Path sourceRoot) {
        try {
            String pathStr = sourceRoot.toAbsolutePath().normalize().toString();
            List<SnapshotSummary> snapshots = backend.listSnapshots();
            
            List<SnapshotSummary> sourceSnapshots = snapshots.stream()
                    .filter(s -> s.deviceId().equals(deviceId))
                    .filter(s -> s.sourcePath().equals(pathStr))
                    .filter(s -> "COMPLETED".equals(s.status()))
                    .toList();

            if (sourceSnapshots.isEmpty()) {
                log.info("event=backup.cache_sync status=no_remote_history action=clear_local_cache");
                db.clearCacheForPath(pathStr);
                return;
            }

            SnapshotSummary latest = sourceSnapshots.get(0);
            String lastSynced = db.getLastSyncedSnapshot(deviceId, pathStr);
            if (!latest.id().toString().equals(lastSynced)) {
                log.info("event=backup.cache_sync status=started action=rebuild_local_index");
                String json = backend.downloadManifest(latest.id());
                com.keeply.agent.model.SnapshotManifest manifest = mapper.readValue(json, com.keeply.agent.model.SnapshotManifest.class);
                db.reconstructIndex(pathStr, manifest);
                db.setLastSyncedSnapshot(deviceId, pathStr, latest.id().toString());
                log.info("event=backup.cache_sync status=completed");
            }
        } catch (Exception e) {
            log.warn("event=backup.cache_sync status=failed message={}", e.getMessage());
        }
    }
}
