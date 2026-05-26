package com.keeply.backend.service;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.keeply.backend.model.FileChunk;
import com.keeply.backend.model.ChunkEntity;
import com.keeply.backend.model.Snapshot;
import com.keeply.backend.model.SnapshotFile;
import com.keeply.backend.model.SnapshotStatus;
import com.keeply.backend.repository.SnapshotFileRepository;
import com.keeply.backend.repository.FileChunkRepository;
import com.keeply.backend.repository.SnapshotRepository;
import com.keeply.backend.repository.ChunkRepository;
import jakarta.persistence.EntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.HexFormat;
import java.util.Set;
import java.util.UUID;
import java.time.Instant;
import java.util.zip.GZIPInputStream;

@Service
public class ManifestParserService {
    private static final Logger log = LoggerFactory.getLogger(ManifestParserService.class);
    private final SnapshotRepository snapshots;
    private final SnapshotFileRepository snapshotFiles;
    private final FileChunkRepository fileChunks;
    private final ObjectStorageService storage;
    private final ObjectMapper mapper;
    private final EntityManager entityManager;
    private final ChunkRepository chunks;
    private final TransferCredentialBroker transferBroker;

    public ManifestParserService(SnapshotRepository snapshots,
                                 SnapshotFileRepository snapshotFiles,
                                 FileChunkRepository fileChunks,
                                 ObjectStorageService storage,
                                 ObjectMapper mapper,
                                 EntityManager entityManager,
                                 ChunkRepository chunks,
                                 TransferCredentialBroker transferBroker) {
        this.snapshots = snapshots;
        this.snapshotFiles = snapshotFiles;
        this.fileChunks = fileChunks;
        this.storage = storage;
        this.mapper = mapper;
        this.entityManager = entityManager;
        this.chunks = chunks;
        this.transferBroker = transferBroker;
    }

    @Async
    @Transactional
    public void auditAndPromoteAsync(UUID snapshotId, UUID sessionId, String stagingPrefix) {
        log.info("event=snapshot.audit status=started snapshot_id={} session_id={}", snapshotId, sessionId);
        Snapshot snapshot = snapshots.findById(snapshotId)
                .orElseThrow(() -> new IllegalArgumentException("Snapshot não encontrado: " + snapshotId));
        UUID userId = snapshot.device.user.id;
        String stagedManifest = stagingPrefix + "manifest.json.gz";

        try (InputStream is = storage.getStream(stagedManifest);
             GZIPInputStream gis = new GZIPInputStream(is)) {
            snapshotFiles.deleteBySnapshotId(snapshotId);
            JsonParser parser = mapper.getFactory().createParser(gis);
            List<FileChunk> chunkBatch = new ArrayList<>(250);
            Set<String> checkedChunks = new HashSet<>();
            int count = 0;

            while (parser.nextToken() != null) {
                if (parser.currentToken() == JsonToken.FIELD_NAME && "files".equals(parser.currentName())) {
                    parser.nextToken();
                    while (parser.nextToken() != JsonToken.END_ARRAY) {
                        parseFile(parser, snapshot, userId, stagingPrefix, checkedChunks, chunkBatch);
                        count++;
                    }
                }
            }
            
            if (!chunkBatch.isEmpty()) {
                saveChunkBatch(chunkBatch);
            }

            storage.copy(stagedManifest, snapshot.manifestKey);
            snapshot.status = SnapshotStatus.COMPLETED;
            snapshots.save(snapshot);
            transferBroker.completeProcessing(sessionId, true, "Auditoria concluída");
            storage.deletePrefix(stagingPrefix);
            log.info("event=snapshot.audit status=completed snapshot_id={} session_id={} files={}", snapshotId, sessionId, count);

        } catch (Exception e) {
            log.error("event=snapshot.audit status=failed snapshot_id={} session_id={} cause={}", snapshotId, sessionId, e.getMessage(), e);
            snapshot.status = SnapshotStatus.FAILED;
            snapshot.errorMessage = e.getMessage();
            snapshots.save(snapshot);
            transferBroker.completeProcessing(sessionId, false, e.getMessage());
            storage.deletePrefix(stagingPrefix);
        }
    }

    private void parseFile(JsonParser parser, Snapshot snapshot, UUID userId, String stagingPrefix,
                           Set<String> checkedChunks, List<FileChunk> chunkBatch) throws java.io.IOException {
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
                        if (checkedChunks.add(chunk.chunkHash)) {
                            auditAndPromoteChunk(userId, stagingPrefix, chunk);
                        }
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

    private void auditAndPromoteChunk(UUID userId, String stagingPrefix, FileChunk reference) {
        String hash = reference.chunkHash.toLowerCase();
        String definitiveKey = ChunkService.chunkKey(userId, hash);
        String stagedKey = stagingPrefix + "chunks/" + hash.substring(0, 2) + "/" + hash.substring(2, 4) + "/" + hash + ".gz";
        if (!storage.exists(stagedKey)) {
            if (!chunks.findByUserIdAndHash(userId, hash).isPresent() || !storage.exists(definitiveKey)) {
                throw new IllegalStateException("Chunk ausente na auditoria: " + hash);
            }
            return;
        }

        validateChunk(stagedKey, hash, reference.originalSize);
        if (!storage.exists(definitiveKey)) {
            storage.copy(stagedKey, definitiveKey);
        }
        if (chunks.findByUserIdAndHash(userId, hash).isEmpty()) {
            ChunkEntity entity = new ChunkEntity();
            entity.userId = userId;
            entity.hash = hash;
            entity.originalSize = reference.originalSize;
            entity.compressedSize = reference.compressedSize;
            entity.storageKey = definitiveKey;
            chunks.save(entity);
        }
    }

    private void validateChunk(String key, String expectedHash, long expectedSize) {
        try (InputStream compressed = storage.getStream(key);
             GZIPInputStream uncompressed = new GZIPInputStream(compressed)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            long size = 0;
            for (int count; (count = uncompressed.read(buffer)) >= 0; ) {
                if (count == 0) {
                    continue;
                }
                digest.update(buffer, 0, count);
                size += count;
            }
            String hash = HexFormat.of().formatHex(digest.digest());
            if (!expectedHash.equalsIgnoreCase(hash) || size != expectedSize) {
                throw new IllegalStateException("Integridade inválida para chunk " + expectedHash);
            }
        } catch (Exception e) {
            throw new IllegalStateException("Falha na auditoria do chunk " + expectedHash, e);
        }
    }

    private void saveChunkBatch(List<FileChunk> batch) {
        fileChunks.saveAll(batch);
        entityManager.flush();
        entityManager.clear();
    }
}
