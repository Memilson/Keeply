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
import java.nio.file.NoSuchFileException;
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
    private static final int PREPARING_PERCENT = 0;
    private static final int SCANNING_PERCENT = 1;
    private static final int PROCESSING_START_PERCENT = 2;
    private static final int PROCESSING_END_PERCENT = 94;
    private static final int MANIFEST_PERCENT = 95;
    private static final int SNAPSHOT_COMPLETION_PERCENT = 97;
    private static final int COMPLETED_PERCENT = 100;
    private final BackendClient backend;
    private final LocalDatabase db;
    private final BackupProgressListener progressListener;
    private static volatile boolean backingUp = false;

    public BackupEngine(BackendClient backend, LocalDatabase db) {
        this(backend, db, BackupProgressListener.NONE);
    }

    public BackupEngine(BackendClient backend, LocalDatabase db, BackupProgressListener progressListener) {
        this.backend = backend;
        this.db = db;
        this.progressListener = progressListener == null ? BackupProgressListener.NONE : progressListener;
    }

    public UUID backup(UUID deviceId, Path sourceRoot) {
        if (backingUp) {
            log.warn("event=backup status=skipped reason=already_running");
            return null;
        }
        backingUp = true;
        try {
            long startTotal = System.nanoTime();
            String sourcePath = sourceRoot.toAbsolutePath().normalize().toString();
            emitProgress(PREPARING_PERCENT, "Preparando backup", sourceRoot);
            validateSourceRoot(sourceRoot);
            autoSyncCache(deviceId, sourceRoot);
            emitProgress(SCANNING_PERCENT, "Escaneando arquivos", sourceRoot);

            long scanStartedAt = System.nanoTime();
            FileScanner.ScanStats preScanStats = FileScanner.walk(sourceRoot, file -> {
            });
            java.util.Queue<FileProcessingFailure> initialScanFailures =
                    collectInitialScanFailures(sourceRoot, preScanStats);
            log.info("event=backup.prescan status=completed files={} ignored_directories={} failed_entries={} traversal_seconds={}",
                    preScanStats.files(), preScanStats.ignoredDirectories(), preScanStats.unreadableEntries(),
                    secondsSince(scanStartedAt));
            if (!initialScanFailures.isEmpty()) {
                throw initialScanFailure(sourcePath, initialScanFailures);
            }

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
                ChunkCodec writeCodec = new CompressionService().writeCodec();
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
                java.util.concurrent.ConcurrentLinkedQueue<FileProcessingFailure> fileFailures =
                        new java.util.concurrent.ConcurrentLinkedQueue<>();
                AtomicInteger maxQueueDepth = new AtomicInteger();
                AtomicInteger processedEntries = new AtomicInteger(0);
                totalFiles.set(Math.toIntExact(preScanStats.files()));
                log.info("event=backup.scan status=started");
                long startProcessing = System.nanoTime();
                emitProgress(fileProcessingPercent(0, preScanStats.files()),
                        processingMessage(0, preScanStats.files(), null), sourceRoot);
                FileScanner.ScanStats processingScanStats = FileScanner.walk(sourceRoot, file -> {
                        String relativePath = sourceRoot.relativize(file).toString().replace('\\', '/');
                        emitProgress(fileProcessingPercent(processedEntries.get(), preScanStats.files()),
                                processingMessage(processedEntries.get(), preScanStats.files(), relativePath), sourceRoot);
                        try {
                            int currentProcessed = processedEntries.get() + 1;
                            if (currentProcessed % 1000 == 0) {
                                log.info("event=backup.progress files_processed={} bytes_processed={}",
                                        currentProcessed, totalOriginalSize.get());
                            }

                            long size = Files.size(file);
                            long mtime = Files.getLastModifiedTime(file).toMillis();
                            totalOriginalSize.addAndGet(size);

                            long cacheStart = System.nanoTime();
                            int cachedChunks = reuseCachedFile(sourcePath, relativePath, size, mtime, sessionChunks);
                            cacheValidationNanos.addAndGet(System.nanoTime() - cacheStart);
                            if (cachedChunks >= 0) {
                                log.debug("Cache hit: {}", relativePath);
                                filesCached.incrementAndGet();
                                chunksReused.addAndGet(cachedChunks);
                            } else {
                                log.debug("Cache miss: {}", relativePath);
                                filesChanged.incrementAndGet();

                                // Leitura única: hash + compressão em um único pass.
                                // Chunks candidatos (não confirmados no servidor) são comprimidos
                                // imediatamente e guardados em temp files. Após o checkChunks HTTP,
                                // apenas os realmente novos são enviados; os existentes têm o temp
                                // file deletado. Elimina a segunda leitura do arquivo.
                                Map<String, CompressedCandidate> candidates = new LinkedHashMap<>();
                                List<PendingChunk> pending = new ArrayList<>();
                                String fileHash;
                                try {
                                    fileHash = chunker.process(file, chunkData -> {
                                        String chunkHash = Sha256Hasher.hashBytes(chunkData.data());
                                        pending.add(new PendingChunk(chunkData.index(), chunkHash, chunkData.originalSize()));
                                        chunksSeen.incrementAndGet();

                                        if (!sessionChunks.containsKey(chunkHash) && !candidates.containsKey(chunkHash)) {
                                            Path tempFile = Files.createTempFile("keeply-chunk-", writeCodec.extension());
                                            long compressedSize;
                                            long compressStart = System.nanoTime();
                                            try {
                                                compressedSize = writeCodec.compressToFile(chunkData.data(), tempFile);
                                                compressionNanos.addAndGet(System.nanoTime() - compressStart);
                                                try (java.io.InputStream decompressed = writeCodec.openDecompressing(Files.newInputStream(tempFile))) {
                                                    String roundtripHash = Sha256Hasher.hashBytes(decompressed.readAllBytes());
                                                    if (!roundtripHash.equals(chunkHash)) {
                                                        throw new IllegalStateException("Integridade do chunk falhou após compressão: " + chunkHash);
                                                    }
                                                }
                                            } catch (Exception e) {
                                                Files.deleteIfExists(tempFile);
                                                throw e;
                                            }
                                            chunksCompressed.incrementAndGet();
                                            candidates.put(chunkHash, new CompressedCandidate(tempFile,
                                                    new ChunkMetadata(chunkHash, chunkData.originalSize(), compressedSize,
                                                            writeCodec.algorithm(), writeCodec.level())));
                                        }
                                    });
                                } catch (Exception e) {
                                    // Limpa todos os temp files criados se o chunking falhar
                                    for (CompressedCandidate c : candidates.values()) {
                                        try { Files.deleteIfExists(c.tempFile()); } catch (Exception ignore) {}
                                    }
                                    throw e;
                                }

                                // Verifica no servidor quais candidatos já existem → atualiza sessionChunks
                                confirmRemoteChunks(pending, sessionChunks);

                                // Roteia candidatos: descarta existentes, enfileira upload dos novos
                                for (Map.Entry<String, CompressedCandidate> entry : candidates.entrySet()) {
                                    String chunkHash = entry.getKey();
                                    CompressedCandidate candidate = entry.getValue();
                                    if (sessionChunks.containsKey(chunkHash)) {
                                        chunksReused.incrementAndGet();
                                        Files.deleteIfExists(candidate.tempFile());
                                    } else {
                                        ChunkMetadata metadata = candidate.metadata();
                                        sessionChunks.put(chunkHash, metadata);
                                        bytesCompressed.addAndGet(metadata.storedSize());
                                        if (db.claimChunkForSession(chunkHash)) {
                                            Path uploadFile = candidate.tempFile();
                                            uploaderPool.execute(() -> {
                                                try {
                                                    long uploadStart = System.nanoTime();
                                                    transferStorage.uploadChunk(chunkHash, uploadFile, writeCodec);
                                                    sentCount.incrementAndGet();
                                                    bytesUploaded.addAndGet(metadata.storedSize());
                                                    db.addKnownChunks(List.of(metadata));
                                                    long latencyMs = (System.nanoTime() - uploadStart) / 1_000_000;
                                                    uploadNanos.addAndGet(latencyMs * 1_000_000);
                                                    log.debug("event=chunk.upload hash={} compressed_bytes={} latency_ms={} in_flight={}",
                                                            chunkHash, metadata.storedSize(), latencyMs,
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
                                            Files.deleteIfExists(candidate.tempFile());
                                        }
                                    }
                                }

                                // Registra todos os chunks no manifesto (sessionChunks agora tem o quadro completo)
                                for (PendingChunk p : pending) {
                                    ChunkMetadata meta = sessionChunks.get(p.hash());
                                    if (meta != null) {
                                        db.addManifestChunk(relativePath, p.index(), p.hash(),
                                                meta.originalSize(), meta.storedSize());
                                    }
                                }
                                db.addManifestFile(relativePath, size, mtime, fileHash);
                            }
                        } catch (NoSuchFileException e) {
                            log.warn("event=backup.file status=failed reason=changed_during_read path={}", relativePath);
                        } catch (Exception e) {
                            if (isToleratedRemovedDuringScan(e)) {
                                log.warn("event=backup.file status=failed reason=changed_during_read path={}", relativePath);
                            } else {
                                log.error("event=backup.file status=failed path={} message={}", relativePath, e.getMessage());
                                fileFailures.add(new FileProcessingFailure(relativePath, "processing_failed", e));
                            }
                        } finally {
                            int processed = processedEntries.incrementAndGet();
                            int percent = fileProcessingPercent(processed, preScanStats.files());
                            emitProgress(percent, processingMessage(processed, preScanStats.files(), relativePath), sourceRoot);
                        }
                    });
                for (FileScanner.ScanFailure failure : processingScanStats.unreadableFailures()) {
                    String failedPath = relativePath(sourceRoot, failure.path());
                    if (isToleratedRemovedDuringScan(failure.cause())) {
                        log.warn("event=backup.file status=failed reason=changed_during_scan path={}", failedPath);
                        continue;
                    }
                    log.error("event=backup.file status=failed reason=unreadable_entry path={} message={}",
                            failedPath, failure.cause().getMessage());
                    fileFailures.add(new FileProcessingFailure(failedPath, "unreadable_entry", failure.cause()));
                }
                log.info("event=backup.scan status=completed files={} ignored_directories={} failed_entries={} traversal_seconds={}",
                        processingScanStats.files(), processingScanStats.ignoredDirectories(), processingScanStats.unreadableEntries(),
                        secondsSince(startProcessing));
                
                // Finaliza o processamento em background
                uploaderPool.shutdown();
                while (!uploaderPool.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)) {
                    log.info("event=backup.upload status=waiting_for_pending_workers");
                }
                if (!uploadErrors.isEmpty()) {
                    throw new IllegalStateException("Falha ao enviar um ou mais chunks", uploadErrors.peek());
                }
                if (!fileFailures.isEmpty()) {
                    throw incompleteSnapshotFailure(snapshotId, transferStorage.sessionId(), sourcePath, fileFailures);
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
                    emitProgress(MANIFEST_PERCENT, "Enviando manifesto", sourceRoot);
                    db.writeManifestZstd(manifestFile, snapshotId.toString(), sourcePath);
                    transferStorage.uploadManifest(manifestFile);
                    emitProgress(SNAPSHOT_COMPLETION_PERCENT, "Concluindo snapshot", sourceRoot);
                    backend.completeSnapshot(snapshotId, transferStorage.sessionId(), totalFiles.get(), totalOriginalSize.get(), totalCompressedSize);
                    emitProgress(COMPLETED_PERCENT, "Backup concluído", sourceRoot);
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
                if (e instanceof BackupSnapshotException backupError) {
                    throw backupError;
                }
                throw new BackupSnapshotException(snapshotId, transferStorage.sessionId(), sourcePath,
                        "Backup falhou antes da conclusao do snapshot: " + safeMessage(e), e);
            }
        } finally {
            backingUp = false;
        }
    }

    private int fileProcessingPercent(int processedFiles, long expectedFiles) {
        if (expectedFiles <= 0) {
            return PROCESSING_START_PERCENT;
        }
        double ratio = Math.min(1.0, processedFiles / (double) expectedFiles);
        int processingRange = PROCESSING_END_PERCENT - PROCESSING_START_PERCENT;
        return Math.max(PROCESSING_START_PERCENT,
                Math.min(PROCESSING_END_PERCENT,
                        PROCESSING_START_PERCENT + (int) Math.floor(ratio * processingRange)));
    }

    private String processingMessage(long processedFiles, long totalFiles, String currentFile) {
        String message = "Processando arquivos (" + formatProgressCount(processedFiles)
                + " / " + formatProgressCount(totalFiles) + ")";
        if (currentFile == null || currentFile.isBlank()) {
            return message;
        }
        return message + " - atual: " + currentFile;
    }

    private String formatProgressCount(long value) {
        return String.format(new java.util.Locale("pt", "BR"), "%,d", value).replace(',', '.');
    }

    private void emitProgress(int percent, String message, Path sourceRoot) {
        try {
            progressListener.onProgress(new BackupProgressListener.BackupProgress(percent, message, sourceRoot));
        } catch (Exception e) {
            log.warn("event=backup.progress_listener status=failed message={}", e.getMessage());
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

    private BackupSnapshotException incompleteSnapshotFailure(UUID snapshotId, UUID transferSessionId, String sourcePath,
                                                              java.util.Queue<FileProcessingFailure> fileFailures) {
        FileProcessingFailure firstFailure = fileFailures.peek();
        String firstFailureDetail = firstFailure == null
                ? "sem detalhes do primeiro arquivo"
                : firstFailure.path() + " (" + firstFailure.reason() + ")";
        String message = "Snapshot incompleto: "
                + fileFailures.size() + " erro(s) real(is) impediram a leitura/processamento dos arquivos. "
                + "Primeira falha: " + firstFailureDetail;
        return new BackupSnapshotException(snapshotId, transferSessionId, sourcePath, message,
                firstFailure == null ? null : firstFailure.cause());
    }

    private java.util.Queue<FileProcessingFailure> collectInitialScanFailures(Path sourceRoot, FileScanner.ScanStats scanStats) {
        java.util.Queue<FileProcessingFailure> failures = new java.util.ArrayDeque<>();
        for (FileScanner.ScanFailure failure : scanStats.unreadableFailures()) {
            String failedPath = relativePath(sourceRoot, failure.path());
            if (isToleratedRemovedDuringScan(failure.cause())) {
                log.warn("event=backup.file status=failed reason=changed_during_scan path={}", failedPath);
                continue;
            }
            log.error("event=backup.file status=failed reason=unreadable_entry_during_prescan path={} message={}",
                    failedPath, failure.cause().getMessage());
            failures.add(new FileProcessingFailure(failedPath, "unreadable_entry_during_prescan", failure.cause()));
        }
        return failures;
    }

    private IllegalStateException initialScanFailure(String sourcePath, java.util.Queue<FileProcessingFailure> failures) {
        FileProcessingFailure firstFailure = failures.peek();
        String firstFailureDetail = firstFailure == null
                ? "sem detalhes do primeiro arquivo"
                : firstFailure.path() + " (" + firstFailure.reason() + ")";
        return new IllegalStateException("Falha ao escanear arquivos antes de iniciar o snapshot em "
                + sourcePath + ": " + failures.size() + " entrada(s) ilegível(is). Primeira falha: "
                + firstFailureDetail, firstFailure == null ? null : firstFailure.cause());
    }

    private static String relativePath(Path root, Path path) {
        try {
            return root.toAbsolutePath().normalize().relativize(path.toAbsolutePath().normalize())
                    .toString().replace('\\', '/');
        } catch (Exception e) {
            return path.toString();
        }
    }

    private static String safeMessage(Throwable throwable) {
        return throwable.getMessage() == null ? throwable.getClass().getSimpleName() : throwable.getMessage();
    }

    private static void validateSourceRoot(Path sourceRoot) {
        if (!Files.exists(sourceRoot)) {
            throw new IllegalStateException("Pasta de origem nao existe: " + sourceRoot.toAbsolutePath().normalize());
        }
        if (!Files.isDirectory(sourceRoot)) {
            throw new IllegalStateException("Origem do backup nao e uma pasta: " + sourceRoot.toAbsolutePath().normalize());
        }
        if (!Files.isReadable(sourceRoot)) {
            throw new IllegalStateException("Pasta de origem nao pode ser lida: " + sourceRoot.toAbsolutePath().normalize());
        }
    }

    private static boolean isToleratedRemovedDuringScan(Throwable throwable) {
        while (throwable != null) {
            if (throwable instanceof NoSuchFileException) {
                return true;
            }
            throwable = throwable.getCause();
        }
        return false;
    }

    private static String secondsSince(long start) {
        return String.format(java.util.Locale.ROOT, "%.2f", (System.nanoTime() - start) / 1_000_000_000.0);
    }

    private record PendingChunk(int index, String hash, int originalSize) {
    }

    private record FileProcessingFailure(String path, String reason, Exception cause) {
    }

    private record CompressedCandidate(Path tempFile, ChunkMetadata metadata) {
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
