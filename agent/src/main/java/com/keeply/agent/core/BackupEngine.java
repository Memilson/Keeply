package com.keeply.agent.core;

import com.keeply.agent.api.BackendClient;
import com.keeply.agent.model.SnapshotSummary;
import com.keeply.agent.model.StartedSnapshot;
import com.keeply.agent.model.TransferCredentials;
import com.keeply.agent.model.ChunkMetadata;
import com.github.luben.zstd.ZstdInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class BackupEngine {
    private static final Logger log = LoggerFactory.getLogger(BackupEngine.class);
    static final int DEFAULT_CHUNK_UPLOAD_WORKERS = 4;
    static final int DEFAULT_CHUNK_UPLOAD_QUEUE_SIZE = 16;
    private static final long AUDIT_POLL_INTERVAL_MILLIS = 5_000L;
    private static final long DEFAULT_AUDIT_TIMEOUT_SECONDS = 3_600L;
    private final BackendClient backend;
    private final LocalDatabase db;

    public BackupEngine(BackendClient backend, LocalDatabase db) {
        this.backend = backend;
        this.db = db;
    }

    public UUID backup(UUID deviceId, Path sourceRoot) {
        long startTotal = System.nanoTime();
        String sourcePath = sourceRoot.toAbsolutePath().normalize().toString();
        autoSyncCache(deviceId, sourceRoot);

        StartedSnapshot started = backend.startSnapshot(deviceId, sourcePath);
        UUID snapshotId = started.snapshot().id();
        DirectTransferStorage transferStorage = new DirectTransferStorage(backend, started.transfer());

        try {
            log.info("event=backup.snapshot status=started snapshot_id={} source_path={}", snapshotId, sourcePath);

            db.clearBackupManifest();

            AtomicInteger sentCount = new AtomicInteger(0);
            AtomicInteger failedBatchItems = new AtomicInteger(0);
            AtomicInteger totalFiles = new AtomicInteger(0);
            AtomicInteger filesCached = new AtomicInteger(0);
            AtomicInteger filesChanged = new AtomicInteger(0);
            AtomicInteger chunksSeen = new AtomicInteger(0);
            AtomicInteger chunksCompressed = new AtomicInteger(0);
            AtomicInteger chunksReused = new AtomicInteger(0);
            AtomicLong totalOriginalSize = new AtomicLong(0);
            AtomicLong bytesCompressed = new AtomicLong(0);
            AtomicLong bytesUploaded = new AtomicLong(0);
            AtomicLong cacheValidationNanos = new AtomicLong(0);
            AtomicLong compressionNanos = new AtomicLong(0);
            AtomicLong uploadNanos = new AtomicLong(0);
            ContentDefinedChunker chunker = new ContentDefinedChunker();
            Map<String, ChunkMetadata> sessionChunks = new ConcurrentHashMap<>();

            int uploadWorkers = Integer.getInteger("keeply.agent.upload.workers", DEFAULT_CHUNK_UPLOAD_WORKERS);
            int uploadQueueSize = Integer.getInteger("keeply.agent.upload.queue-size", DEFAULT_CHUNK_UPLOAD_QUEUE_SIZE);
            java.util.concurrent.ThreadPoolExecutor uploaderPool = new java.util.concurrent.ThreadPoolExecutor(
                    uploadWorkers,
                    uploadWorkers,
                    0L,
                    java.util.concurrent.TimeUnit.MILLISECONDS,
                    new java.util.concurrent.ArrayBlockingQueue<>(uploadQueueSize),
                    new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy()
            );
            java.util.concurrent.ConcurrentLinkedQueue<Exception> uploadErrors =
                    new java.util.concurrent.ConcurrentLinkedQueue<>();
            AtomicInteger maxQueueDepth = new AtomicInteger();
            log.info("event=backup.scan status=started");
            long startProcessing = System.nanoTime();
            FileScanner.ScanStats scanStats = FileScanner.walk(sourceRoot, file -> {
                    String relativePath = sourceRoot.relativize(file).toString().replace("\\", "/");
                    try {
                        int currentTotal = totalFiles.incrementAndGet();
                        if (currentTotal % 1000 == 0) {
                            log.info("event=backup.progress files_processed={} bytes_processed={}",
                                    currentTotal, totalOriginalSize.get());
                        }

                        long size = Files.size(file);
                        long mtime = Files.getLastModifiedTime(file).toMillis();
                        totalOriginalSize.addAndGet(size);

                        long cacheStart = System.nanoTime();
                        int cachedChunks = reuseCachedFile(sourcePath, relativePath, size, mtime, sessionChunks);
                        cacheValidationNanos.addAndGet(System.nanoTime() - cacheStart);
                        if (cachedChunks >= 0) {
                            log.debug("📄 Cache hit: {}", relativePath);
                            filesCached.incrementAndGet();
                            chunksReused.addAndGet(cachedChunks);
                        } else {
                            log.debug("📄 Cache miss: {}", relativePath);
                            filesChanged.incrementAndGet();
                            List<PendingChunk> pending = new ArrayList<>();
                            String fileHash = chunker.process(file, chunkData -> {
                                String chunkHash = Sha256Hasher.hashBytes(chunkData.data());
                                pending.add(new PendingChunk(chunkData.index(), chunkHash, chunkData.originalSize()));
                                chunksSeen.incrementAndGet();
                            });
                            confirmRemoteChunks(pending, sessionChunks);
                            chunker.process(file, chunkData -> {
                                PendingChunk reference = pending.get(chunkData.index());
                                String chunkHash = reference.hash();
                                int originalSize = reference.originalSize();
                                ChunkMetadata reusable = sessionChunks.get(chunkHash);
                                if (reusable != null) {
                                    db.addManifestChunk(relativePath, chunkData.index(), chunkHash,
                                            reusable.originalSize(), reusable.compressedSize());
                                    chunksReused.incrementAndGet();
                                    return;
                                }
                                Path compressedFile = Files.createTempFile("keeply-chunk-", ".zst");
                                long compressedSize;
                                long compressStart = System.nanoTime();
                                try {
                                    compressedSize = ZstdCompressor.compressToFile(chunkData.data(), compressedFile);
                                    compressionNanos.addAndGet(System.nanoTime() - compressStart);
                                } catch (Exception e) {
                                    Files.deleteIfExists(compressedFile);
                                    throw e;
                                }
                                ChunkMetadata metadata = new ChunkMetadata(chunkHash, originalSize, compressedSize);
                                sessionChunks.put(chunkHash, metadata);
                                chunksCompressed.incrementAndGet();
                                bytesCompressed.addAndGet(compressedSize);
                                db.addManifestChunk(relativePath, chunkData.index(), chunkHash, originalSize, compressedSize);

                                if (db.claimChunkForSession(chunkHash)) {
                                    Path uploadFile = compressedFile;
                                    uploaderPool.execute(() -> {
                                        try {
                                            long uploadStart = System.nanoTime();
                                            transferStorage.uploadChunk(chunkHash, uploadFile);
                                            sentCount.incrementAndGet();
                                            bytesUploaded.addAndGet(compressedSize);
                                            db.addKnownChunks(List.of(metadata));
                                            uploadNanos.addAndGet(System.nanoTime() - uploadStart);
                                            long latencyMs = (System.nanoTime() - uploadStart) / 1_000_000;
                                            log.debug("event=chunk.upload hash={} compressed_bytes={} stored={} latency_ms={} in_flight={}",
                                                    chunkHash, compressedSize, true, latencyMs,
                                                    uploaderPool.getActiveCount());
                                        } catch (Exception e) {
                                            failedBatchItems.incrementAndGet();
                                            uploadErrors.add(e);
                                            log.error("event=chunk.upload status=failed hash={} message={}", chunkHash, e.getMessage(), e);
                                        } finally {
                                            try {
                                                Files.deleteIfExists(uploadFile);
                                            } catch (Exception e) {
                                                log.warn("event=chunk.temp_file status=cleanup_failed path={} message={}", uploadFile, e.getMessage());
                                            }
                                        }
                                    });
                                    maxQueueDepth.accumulateAndGet(uploaderPool.getQueue().size(), Math::max);
                                } else {
                                    chunksReused.incrementAndGet();
                                    Files.deleteIfExists(compressedFile);
                                }
                            });

                            db.addManifestFile(relativePath, size, mtime, fileHash);
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
            log.info("event=backup.scan status=completed files={} directories_pruned={} unreadable_entries={} traversal_seconds={}",
                    scanStats.files(), scanStats.prunedDirectories(), scanStats.unreadableEntries(),
                    secondsSince(startProcessing));
            
            // Finaliza o processamento em background
            uploaderPool.shutdown();
            while (!uploaderPool.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)) {
                log.info("event=backup.upload status=waiting_for_pending_workers");
            }
            if (!uploadErrors.isEmpty()) {
                throw new IllegalStateException("Falha ao enviar um ou mais chunks", uploadErrors.peek());
            }
            long totalCompressedSize = db.totalDistinctCompressedSize();

            double processDuration = (System.nanoTime() - startProcessing) / 1_000_000_000.0;
            double throughputOriginal = (totalOriginalSize.get() / 1024.0 / 1024.0) / processDuration;

            log.info("event=backup.summary files_total={} files_cached={} files_changed={}",
                    totalFiles.get(), filesCached.get(), filesChanged.get());
            log.info("event=backup.summary chunks_seen={} chunks_compressed={} chunks_reused={} chunks_uploaded={} chunks_failed={}",
                    chunksSeen.get(), chunksCompressed.get(), chunksReused.get(), sentCount.get(), failedBatchItems.get());
            log.info("event=backup.summary size_original_mb={} size_compressed_mb={}",
                    totalOriginalSize.get() / 1024 / 1024, totalCompressedSize / 1024 / 1024);
            log.info("event=backup.summary processing_seconds={} throughput_mb_s={} cache_validation_ms={} compression_ms={} upload_ms={} compressed_bytes={} uploaded_bytes={} upload_queue_max={}",
                    String.format(java.util.Locale.ROOT, "%.2f", processDuration),
                    String.format(java.util.Locale.ROOT, "%.2f", throughputOriginal),
                    cacheValidationNanos.get() / 1_000_000, compressionNanos.get() / 1_000_000,
                    uploadNanos.get() / 1_000_000, bytesCompressed.get(), bytesUploaded.get(), maxQueueDepth.get());

            long startManifest = System.nanoTime();

            Path manifestFile = Files.createTempFile("keeply-manifest-" + snapshotId, ".json.zst");
            try {
                db.writeManifestZstd(manifestFile, snapshotId.toString(), sourcePath);
                transferStorage.uploadManifest(manifestFile);
                backend.completeSnapshot(snapshotId, transferStorage.sessionId(), totalFiles.get(), totalOriginalSize.get(), totalCompressedSize);
                awaitSnapshotAudit(snapshotId);
            } finally {
                Files.deleteIfExists(manifestFile);
            }
            db.saveManifestToCache(sourcePath);

            double manifestDuration = (System.nanoTime() - startManifest) / 1_000_000_000.0;
            log.info("event=backup.manifest status=completed files={} duration_seconds={}",
                    totalFiles.get(),
                    String.format(java.util.Locale.ROOT, "%.2f", manifestDuration));

            db.setLastSyncedSnapshot(deviceId, sourcePath, snapshotId.toString());
            db.clearBackupManifest(); 

            double totalDuration = (System.nanoTime() - startTotal) / 1_000_000_000.0;
            log.info("event=backup.snapshot status=uploads_completed_pending_audit snapshot_id={} total_duration_seconds={} chunks_sent={}",
                    snapshotId,
                    String.format(java.util.Locale.ROOT, "%.2f", totalDuration),
                    sentCount.get());

            return snapshotId;
        } catch (Exception e) {
            try {
                backend.cancelTransferSession(transferStorage.sessionId());
            } catch (Exception cancelError) {
                log.warn("event=backup.transfer_session status=cancel_failed message={}", cancelError.getMessage());
            }
            backend.failSnapshot(snapshotId, e.getMessage());
            throw new IllegalStateException("Backup falhou", e);
        }
    }

    private int reuseCachedFile(String sourcePath, String relativePath, long size, long mtime,
                                Map<String, ChunkMetadata> sessionChunks) {
        List<ChunkMetadata> cached = db.cachedChunksIfUnchanged(sourcePath, relativePath, size, mtime);
        if (cached.isEmpty()) {
            return db.copyCachedFileToManifestIfValid(sourcePath, relativePath, size, mtime);
        }
        for (int from = 0; from < cached.size(); from += 1000) {
            List<ChunkMetadata> page = cached.subList(from, Math.min(from + 1000, cached.size()));
            List<String> toValidate = page.stream().map(ChunkMetadata::hash)
                    .filter(hash -> !sessionChunks.containsKey(hash)).distinct().toList();
            if (!toValidate.isEmpty()) {
                BackendClient.CheckChunksResult result = backend.checkChunks(toValidate);
                if (!result.missing().isEmpty()) {
                    db.removeKnownChunks(result.missing());
                    return -1;
                }
                db.addKnownChunks(result.existing());
                db.addSessionKnownChunks(result.existing());
                result.existing().forEach(chunk -> sessionChunks.put(chunk.hash(), chunk));
            }
        }
        return db.copyCachedFileToManifestIfValid(sourcePath, relativePath, size, mtime);
    }

    private void confirmRemoteChunks(List<PendingChunk> pending, Map<String, ChunkMetadata> sessionChunks) {
        Map<String, PendingChunk> candidates = new LinkedHashMap<>();
        for (PendingChunk chunk : pending) {
            if (!sessionChunks.containsKey(chunk.hash())) {
                candidates.putIfAbsent(chunk.hash(), chunk);
            }
        }
        List<String> hashes = new ArrayList<>(candidates.keySet());
        for (int from = 0; from < hashes.size(); from += 1000) {
            BackendClient.CheckChunksResult result = backend.checkChunks(
                    hashes.subList(from, Math.min(from + 1000, hashes.size())));
            for (ChunkMetadata existing : result.existing()) {
                PendingChunk candidate = candidates.get(existing.hash());
                if (candidate != null && existing.originalSize() != candidate.originalSize()) {
                    throw new IllegalStateException("Metadados inconsistentes para chunk " + existing.hash());
                }
                sessionChunks.put(existing.hash(), existing);
            }
            db.addKnownChunks(result.existing());
            db.addSessionKnownChunks(result.existing());
        }
    }

    private static String secondsSince(long start) {
        return String.format(java.util.Locale.ROOT, "%.2f", (System.nanoTime() - start) / 1_000_000_000.0);
    }

    private void awaitSnapshotAudit(UUID snapshotId) throws InterruptedException {
        long timeoutSeconds = Long.getLong("keeply.agent.snapshot.audit-timeout-seconds", DEFAULT_AUDIT_TIMEOUT_SECONDS);
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(timeoutSeconds);
        while (true) {
            SnapshotSummary snapshot = backend.listSnapshots().stream()
                    .filter(s -> s.id().equals(snapshotId))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("Snapshot não encontrado após conclusão: " + snapshotId));
            if ("COMPLETED".equals(snapshot.status())) {
                log.info("event=backup.snapshot status=audit_completed snapshot_id={}", snapshotId);
                return;
            }
            if ("FAILED".equals(snapshot.status())) {
                throw new IllegalStateException("Auditoria do snapshot falhou: "
                        + (snapshot.errorMessage() == null ? snapshotId : snapshot.errorMessage()));
            }
            if (System.nanoTime() >= deadline) {
                throw new IllegalStateException("Auditoria do snapshot excedeu o tempo limite: " + snapshotId);
            }
            log.info("event=backup.snapshot status=waiting_audit snapshot_id={} remote_status={}",
                    snapshotId, snapshot.status());
            Thread.sleep(AUDIT_POLL_INTERVAL_MILLIS);
        }
    }

    private record PendingChunk(int index, String hash, int originalSize) {
    }

    private void autoSyncCache(UUID deviceId, Path sourceRoot) {
        try {
            String pathStr = sourceRoot.toAbsolutePath().normalize().toString();
            List<SnapshotSummary> snapshots = backend.listSnapshots();
            
            List<SnapshotSummary> sourceSnapshots = snapshots.stream()
                    .filter(s -> Objects.equals(s.deviceId(), deviceId))
                    .filter(s -> Objects.equals(s.sourcePath(), pathStr))
                    .filter(s -> "COMPLETED".equals(s.status()))
                    .toList();
            if (sourceSnapshots.isEmpty()) {
                sourceSnapshots = snapshots.stream()
                        .filter(s -> Objects.equals(s.sourcePath(), pathStr))
                        .filter(s -> "COMPLETED".equals(s.status()))
                        .toList();
                if (!sourceSnapshots.isEmpty()) {
                    log.info("event=backup.cache_sync status=using_source_history scope=any_device source_path={}", pathStr);
                }
            }

            if (sourceSnapshots.isEmpty()) {
                log.info("event=backup.cache_sync status=no_remote_history action=clear_local_cache");
                db.clearCacheForPath(pathStr);
                return;
            }

            SnapshotSummary latest = sourceSnapshots.get(0);
            String lastSynced = db.getLastSyncedSnapshot(deviceId, pathStr);
            if (!latest.id().toString().equals(lastSynced)) {
                log.info("event=backup.cache_sync status=started action=rebuild_local_index");
                TransferCredentials credentials = backend.startRestoreSession(latest.id());
                DirectTransferStorage storage = new DirectTransferStorage(backend, credentials);
                try (var zstdStream = storage.openManifest(latest.id());
                     var manifestStream = new ZstdInputStream(zstdStream)) {
                    db.reconstructIndex(pathStr, manifestStream);
                } finally {
                    backend.finishTransferSession(credentials.transferSessionId());
                }
                db.setLastSyncedSnapshot(deviceId, pathStr, latest.id().toString());
                log.info("event=backup.cache_sync status=completed");
            }
        } catch (Exception e) {
            log.warn("event=backup.cache_sync status=failed message={}", e.getMessage());
            throw new IllegalStateException("Falha ao sincronizar cache local com histórico remoto", e);
        }
    }
}
