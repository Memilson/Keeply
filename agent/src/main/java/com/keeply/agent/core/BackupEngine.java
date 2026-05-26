package com.keeply.agent.core;

import com.keeply.agent.api.BackendClient;
import com.keeply.agent.model.SnapshotSummary;
import com.keeply.agent.model.StartedSnapshot;
import com.keeply.agent.model.TransferCredentials;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.GZIPInputStream;

public class BackupEngine {
    private static final Logger log = LoggerFactory.getLogger(BackupEngine.class);
    static final int CHUNK_UPLOAD_WORKERS = 4;
    static final int CHUNK_UPLOAD_QUEUE_SIZE = 4;
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
            long knownCount = 0;
            long validCount = 0;
            String afterHash = "";
            for (;;) {
                List<String> page = db.getKnownChunksPage(afterHash, 1000);
                if (page.isEmpty()) break;
                afterHash = page.get(page.size() - 1);
                knownCount += page.size();
                Set<String> existing = backend.checkChunks(page);
                validCount += existing.size();
                db.addSessionKnownChunks(existing);
                List<String> missing = page.stream().filter(hash -> !existing.contains(hash)).toList();
                if (!missing.isEmpty()) db.removeKnownChunks(missing);
            }
            log.info("event=backup.chunk_integrity_check status=completed valid={} total={}", validCount, knownCount);

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
                    CHUNK_UPLOAD_WORKERS,
                    CHUNK_UPLOAD_WORKERS,
                    0L,
                    java.util.concurrent.TimeUnit.MILLISECONDS,
                    new java.util.concurrent.ArrayBlockingQueue<>(CHUNK_UPLOAD_QUEUE_SIZE),
                    new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy()
            );
            java.util.concurrent.ConcurrentLinkedQueue<Exception> uploadErrors =
                    new java.util.concurrent.ConcurrentLinkedQueue<>();
            // Pré-scan rápido para contar arquivos e permitir porcentagem
            log.info("event=backup.scan status=started");
            long totalFilesToProcess;
            try (var preScanStream = FileScanner.scan(sourceRoot)) {
                totalFilesToProcess = preScanStream.count();
            }
            log.info("event=backup.scan status=completed files_total={}", totalFilesToProcess);

            AtomicInteger lastLoggedPercentage = new AtomicInteger(-1);
            long startProcessing = System.nanoTime();
            printProgress(0);
            try (var stream = FileScanner.scan(sourceRoot)) {
                stream.forEach(file -> {
                    String relativePath = sourceRoot.relativize(file).toString().replace("\\", "/");
                    try {
                        int currentTotal = totalFiles.incrementAndGet();
                        if (totalFilesToProcess > 0) {
                            int percentage = (int) ((currentTotal * 100) / totalFilesToProcess);
                            if (percentage > lastLoggedPercentage.get()) {
                                lastLoggedPercentage.set(percentage);
                                printProgress(percentage);
                            }
                        }

                        long size = Files.size(file);
                        long mtime = Files.getLastModifiedTime(file).toMillis();
                        totalOriginalSize.addAndGet(size);

                        int cachedChunks = db.copyCachedFileToManifestIfValid(sourcePath, relativePath, size, mtime);
                        if (cachedChunks >= 0) {
                            log.debug("📄 Cache hit: {}", relativePath);
                            filesCached.incrementAndGet();
                            chunksReused.addAndGet(cachedChunks);
                        } else {
                            log.debug("📄 Cache miss: {}", relativePath);
                            String fileHash = chunker.process(file, chunkData -> {
                                byte[] raw = chunkData.data();
                                String chunkHash = Sha256Hasher.hashBytes(raw);
                                int originalSize = raw.length;
                                Path compressedFile = Files.createTempFile("keeply-chunk-", ".gz");
                                long compressedSize;
                                try {
                                    compressedSize = GzipCompressor.compressToFile(raw, compressedFile);
                                } catch (Exception e) {
                                    Files.deleteIfExists(compressedFile);
                                    throw e;
                                }
                                db.addManifestChunk(relativePath, chunkData.index(), chunkHash, originalSize, compressedSize);

                                if (db.claimChunkForSession(chunkHash)) {
                                    Path uploadFile = compressedFile;
                                    uploaderPool.execute(() -> {
                                        try {
                                            long uploadStart = System.nanoTime();
                                            transferStorage.uploadChunk(chunkHash, uploadFile);
                                            sentCount.incrementAndGet();
                                            db.addKnownChunks(Set.of(chunkHash));
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
                                } else {
                                    chunksReused.incrementAndGet();
                                    Files.deleteIfExists(compressedFile);
                                }
                            });

                            db.addManifestFile(relativePath, size, mtime, fileHash);
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
            } finally {
                System.out.println();
            }
            
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
                    totalFiles.get(), filesCached.get(), totalFiles.get() - filesCached.get());
            log.info("event=backup.summary chunks_generated={} chunks_reused={} chunks_uploaded={}",
                    chunksGenerated.get(), chunksReused.get(), sentCount.get());
            log.info("event=backup.summary chunks_duplicate={} chunks_failed={}",
                    duplicateCount.get(), failedBatchItems.get());
            log.info("event=backup.summary size_original_mb={} size_compressed_mb={}",
                    totalOriginalSize.get() / 1024 / 1024, totalCompressedSize / 1024 / 1024);
            log.info("event=backup.summary processing_seconds={} throughput_mb_s={}",
                    String.format(java.util.Locale.ROOT, "%.2f", processDuration),
                    String.format(java.util.Locale.ROOT, "%.2f", throughputOriginal));

            long startManifest = System.nanoTime();

            Path manifestFile = Files.createTempFile("keeply-manifest-" + snapshotId, ".json.gz");
            try {
                db.writeManifestGzip(manifestFile, snapshotId.toString(), sourcePath);
                transferStorage.uploadManifest(manifestFile);
                backend.completeSnapshot(snapshotId, transferStorage.sessionId(), totalFiles.get(), totalOriginalSize.get(), totalCompressedSize);
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
            log.info("event=backup.snapshot status=completed snapshot_id={} total_duration_seconds={} chunks_sent={}",
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

    private static void printProgress(int percentage) {
        System.out.print("\rBackup: %3d%%".formatted(percentage));
        System.out.flush();
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
                TransferCredentials credentials = backend.startRestoreSession(latest.id());
                DirectTransferStorage storage = new DirectTransferStorage(backend, credentials);
                try (var gzipStream = storage.openManifest(latest.id());
                     var manifestStream = new GZIPInputStream(gzipStream)) {
                    db.reconstructIndex(pathStr, manifestStream);
                } finally {
                    backend.finishTransferSession(credentials.transferSessionId());
                }
                db.setLastSyncedSnapshot(deviceId, pathStr, latest.id().toString());
                log.info("event=backup.cache_sync status=completed");
            }
        } catch (Exception e) {
            log.warn("event=backup.cache_sync status=failed message={}", e.getMessage());
        }
    }
}
