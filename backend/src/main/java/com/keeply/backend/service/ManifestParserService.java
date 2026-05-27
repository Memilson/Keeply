package com.keeply.backend.service;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.luben.zstd.ZstdInputStream;
import com.keeply.backend.model.FileChunk;
import com.keeply.backend.model.ChunkEntity;
import com.keeply.backend.model.Snapshot;
import com.keeply.backend.model.SnapshotFile;
import com.keeply.backend.model.SnapshotStatus;
import com.keeply.backend.repository.SnapshotFileRepository;
import com.keeply.backend.repository.FileChunkRepository;
import com.keeply.backend.repository.SnapshotRepository;
import com.keeply.backend.repository.ChunkRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.time.Instant;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class ManifestParserService {
    private static final Logger log = LoggerFactory.getLogger(ManifestParserService.class);
    private static final int DEFAULT_MANIFEST_READ_ATTEMPTS = 3;
    private final SnapshotRepository snapshots;
    private final SnapshotFileRepository snapshotFiles;
    private final FileChunkRepository fileChunks;
    private final ObjectStorageService storage;
    private final ObjectMapper mapper;
    private final ChunkRepository chunks;
    private final TransferCredentialBroker transferBroker;
    private final int auditWorkers;
    private final int auditQueueSize;

    public ManifestParserService(SnapshotRepository snapshots,
                                 SnapshotFileRepository snapshotFiles,
                                 FileChunkRepository fileChunks,
                                 ObjectStorageService storage,
                                 ObjectMapper mapper,
                                 ChunkRepository chunks,
                                 TransferCredentialBroker transferBroker,
                                 @Value("${keeply.audit.workers:4}") int auditWorkers,
                                 @Value("${keeply.audit.queue-size:16}") int auditQueueSize) {
        this.snapshots = snapshots;
        this.snapshotFiles = snapshotFiles;
        this.fileChunks = fileChunks;
        this.storage = storage;
        this.mapper = mapper;
        this.chunks = chunks;
        this.transferBroker = transferBroker;
        this.auditWorkers = auditWorkers;
        this.auditQueueSize = auditQueueSize;
    }

    @Async
    public void auditAndPromoteAsync(UUID snapshotId, UUID sessionId, String stagingPrefix, UUID userId) {
        long auditStart = System.nanoTime();
        log.info("event=snapshot.audit status=started snapshot_id={} session_id={}", snapshotId, sessionId);
        String stagedManifest = stagingPrefix + "manifest.json.zst";
        Snapshot snapshot = null;

        try {
            snapshot = snapshots.findById(snapshotId)
                    .orElseThrow(() -> new IllegalArgumentException("Snapshot não encontrado: " + snapshotId));
            long parseStart = System.nanoTime();
            Map<String, ChunkReference> references = new LinkedHashMap<>();
            int count = parseManifestWithRetries(snapshotId, stagedManifest, snapshot, references);
            long parseMs = elapsedMillis(parseStart);
            long promotionStart = System.nanoTime();
            Set<String> existing = findExistingHashes(userId, new ArrayList<>(references.keySet()));
            List<ChunkReference> fresh = references.entrySet().stream()
                    .filter(entry -> !existing.contains(entry.getKey()))
                    .map(Map.Entry::getValue)
                    .toList();
            promoteNewChunks(userId, stagingPrefix, fresh);
            long promotionMs = elapsedMillis(promotionStart);

            storage.copy(stagedManifest, snapshot.manifestKey);
            snapshot.status = SnapshotStatus.COMPLETED;
            snapshot.completedAt = Instant.now();
            snapshots.save(snapshot);
            transferBroker.completeProcessing(sessionId, true, "Auditoria concluída");
            long cleanupStart = System.nanoTime();
            storage.deletePrefix(stagingPrefix);
            log.info("event=snapshot.audit status=completed snapshot_id={} session_id={} files={} chunks_reused={} chunks_new={} parse_persist_ms={} promote_ms={} cleanup_ms={} total_ms={}",
                    snapshotId, sessionId, count, existing.size(), fresh.size(), parseMs, promotionMs,
                    elapsedMillis(cleanupStart), elapsedMillis(auditStart));

        } catch (Exception e) {
            log.error("event=snapshot.audit status=failed snapshot_id={} session_id={} cause={}", snapshotId, sessionId, e.getMessage(), e);
            if (snapshot != null) {
                snapshot.status = SnapshotStatus.FAILED;
                snapshot.errorMessage = e.getMessage();
                snapshots.save(snapshot);
            }
            transferBroker.completeProcessing(sessionId, false, e.getMessage());
            storage.deletePrefix(stagingPrefix);
        }
    }

    private int parseManifestWithRetries(UUID snapshotId, String stagedManifest, Snapshot snapshot,
                                         Map<String, ChunkReference> references) throws Exception {
        int attempts = Integer.getInteger("keeply.audit.manifest-read-attempts", DEFAULT_MANIFEST_READ_ATTEMPTS);
        Exception lastFailure = null;
        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
                references.clear();
                return parseManifest(stagedManifest, snapshot, references);
            } catch (Exception e) {
                lastFailure = e;
                if (!isRetryableManifestReadFailure(e) || attempt == attempts) {
                    throw e;
                }
                long delayMillis = attempt * 1_000L;
                log.warn("event=snapshot.audit.manifest_read status=retrying snapshot_id={} attempt={} max_attempts={} delay_ms={} cause={}",
                        snapshotId, attempt, attempts, delayMillis, e.getMessage());
                try {
                    Thread.sleep(delayMillis);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw interrupted;
                }
            }
        }
        throw lastFailure;
    }

    private int parseManifest(String stagedManifest, Snapshot snapshot,
                              Map<String, ChunkReference> references) throws java.io.IOException {
        int count = 0;
        try (InputStream is = storage.getStream(stagedManifest);
             ZstdInputStream zstd = new ZstdInputStream(is);
             JsonParser parser = mapper.getFactory().createParser(zstd)) {
            snapshotFiles.deleteBySnapshotId(snapshot.id);
            List<FileChunk> chunkBatch = new ArrayList<>(250);
            String compression = null;

            while (parser.nextToken() != null) {
                if (parser.currentToken() == JsonToken.FIELD_NAME && "compression".equals(parser.currentName())) {
                    parser.nextToken();
                    compression = parser.getValueAsString();
                } else if (parser.currentToken() == JsonToken.FIELD_NAME && "files".equals(parser.currentName())) {
                    requireZstdCompression(compression);
                    parser.nextToken();
                    while (parser.nextToken() != JsonToken.END_ARRAY) {
                        parseFile(parser, snapshot, references, chunkBatch);
                        count++;
                    }
                }
            }
            requireZstdCompression(compression);

            if (!chunkBatch.isEmpty()) {
                saveChunkBatch(chunkBatch);
            }
        }
        return count;
    }

    private boolean isRetryableManifestReadFailure(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof java.net.ProtocolException
                    || current instanceof java.io.EOFException
                    || current instanceof java.net.SocketException) {
                return true;
            }
            String message = current.getMessage();
            if (message != null && message.toLowerCase(java.util.Locale.ROOT).contains("unexpected end of stream")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private void parseFile(JsonParser parser, Snapshot snapshot, Map<String, ChunkReference> references,
                           List<FileChunk> chunkBatch) throws java.io.IOException {
        SnapshotFile file = new SnapshotFile();
        file.snapshot = snapshot;
        while (parser.nextToken() != JsonToken.END_OBJECT) {
            if (parser.currentToken() != JsonToken.FIELD_NAME) {
                continue;
            }
            String name = parser.currentName();
            JsonToken value = parser.nextToken();
            switch (name) {
                case "path" -> file.path = parser.getValueAsString();
                case "size" -> file.size = parser.getLongValue();
                case "lastModified" -> file.lastModified = Instant.parse(parser.getValueAsString());
                case "sha256" -> file.sha256 = parser.getValueAsString();
                case "chunks" -> {
                    snapshotFiles.save(file);
                    while (parser.nextToken() != JsonToken.END_ARRAY) {
                        FileChunk chunk = parseChunk(parser, file);
                        references.putIfAbsent(chunk.chunkHash.toLowerCase(),
                                new ChunkReference(chunk.chunkHash.toLowerCase(), chunk.originalSize, chunk.compressedSize));
                        chunkBatch.add(chunk);
                        if (chunkBatch.size() >= 250) {
                            saveChunkBatch(chunkBatch);
                            chunkBatch.clear();
                        }
                    }
                }
                default -> parser.skipChildren();
            }
        }
        if (file.id == null) {
            snapshotFiles.save(file);
        }
    }

    private FileChunk parseChunk(JsonParser parser, SnapshotFile file) throws java.io.IOException {
        FileChunk chunk = new FileChunk();
        chunk.snapshotFile = file;
        while (parser.nextToken() != JsonToken.END_OBJECT) {
            if (parser.currentToken() != JsonToken.FIELD_NAME) continue;
            String name = parser.currentName();
            parser.nextToken();
            switch (name) {
                case "index" -> chunk.chunkIndex = parser.getIntValue();
                case "hash" -> chunk.chunkHash = parser.getValueAsString();
                case "originalSize" -> chunk.originalSize = parser.getLongValue();
                case "compressedSize" -> chunk.compressedSize = parser.getLongValue();
                default -> parser.skipChildren();
            }
        }
        return chunk;
    }

    private Set<String> findExistingHashes(UUID userId, List<String> hashes) {
        java.util.HashSet<String> existing = new java.util.HashSet<>();
        for (int from = 0; from < hashes.size(); from += 1000) {
            List<String> page = hashes.subList(from, Math.min(from + 1000, hashes.size()));
            chunks.findByUserIdAndHashIn(userId, page).stream()
                    .map(chunk -> chunk.hash.toLowerCase())
                    .forEach(existing::add);
        }
        return existing;
    }

    private void promoteNewChunks(UUID userId, String stagingPrefix, List<ChunkReference> references) {
        ThreadPoolExecutor pool = new ThreadPoolExecutor(auditWorkers, auditWorkers, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(auditQueueSize), new ThreadPoolExecutor.CallerRunsPolicy());
        AtomicInteger queueMax = new AtomicInteger();
        try {
            List<CompletableFuture<Void>> tasks = new ArrayList<>(references.size());
            for (ChunkReference reference : references) {
                tasks.add(CompletableFuture.runAsync(() -> promoteChunk(userId, stagingPrefix, reference), pool));
                queueMax.accumulateAndGet(pool.getQueue().size(), Math::max);
            }
            CompletableFuture.allOf(tasks.toArray(CompletableFuture[]::new)).join();
            log.info("event=snapshot.audit.promotion status=completed chunks_new={} queue_max={} workers={}",
                    references.size(), queueMax.get(), auditWorkers);
        } finally {
            pool.shutdown();
        }
    }

    private void promoteChunk(UUID userId, String stagingPrefix, ChunkReference reference) {
        String hash = reference.hash();
        String definitiveKey = ChunkService.chunkKey(userId, hash);
        String stagedKey = stagingPrefix + "chunks/" + hash.substring(0, 2) + "/" + hash.substring(2, 4) + "/" + hash + ".zst";
        if (!storage.exists(stagedKey)) {
            throw new IllegalStateException("Chunk novo ausente no staging: " + hash);
        }

        if (!storage.exists(definitiveKey)) {
            storage.copy(stagedKey, definitiveKey);
        }
        if (chunks.findByUserIdAndHash(userId, hash).isEmpty()) {
            ChunkEntity entity = new ChunkEntity();
            entity.userId = userId;
            entity.hash = hash;
            entity.originalSize = reference.originalSize();
            entity.compressedSize = reference.compressedSize();
            entity.storageKey = definitiveKey;
            chunks.save(entity);
        }
    }

    private void requireZstdCompression(String compression) {
        if (!"ZSTD".equals(compression)) {
            throw new IllegalStateException("Manifesto deve declarar compression=ZSTD");
        }
    }

    private void saveChunkBatch(List<FileChunk> batch) {
        fileChunks.saveAll(batch);
    }

    private long elapsedMillis(long start) {
        return (System.nanoTime() - start) / 1_000_000;
    }

    private record ChunkReference(String hash, long originalSize, long compressedSize) {
    }
}
