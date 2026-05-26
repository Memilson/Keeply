package com.keeply.backend.service;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.keeply.backend.model.FileChunk;
import com.keeply.backend.model.Snapshot;
import com.keeply.backend.model.SnapshotFile;
import com.keeply.backend.model.SnapshotStatus;
import com.keeply.backend.repository.SnapshotFileRepository;
import com.keeply.backend.repository.FileChunkRepository;
import com.keeply.backend.repository.SnapshotRepository;
import jakarta.persistence.EntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
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

    public ManifestParserService(SnapshotRepository snapshots,
                                 SnapshotFileRepository snapshotFiles,
                                 FileChunkRepository fileChunks,
                                 ObjectStorageService storage,
                                 ObjectMapper mapper,
                                 EntityManager entityManager) {
        this.snapshots = snapshots;
        this.snapshotFiles = snapshotFiles;
        this.fileChunks = fileChunks;
        this.storage = storage;
        this.mapper = mapper;
        this.entityManager = entityManager;
    }

    @Async
    @Transactional
    public void parseAsync(UUID snapshotId) {
        log.info("Iniciando indexação do manifesto para o snapshot: {}", snapshotId);
        Snapshot snapshot = snapshots.findById(snapshotId)
                .orElseThrow(() -> new IllegalArgumentException("Snapshot não encontrado: " + snapshotId));

        try (InputStream is = storage.getStream(snapshot.manifestKey);
             GZIPInputStream gis = new GZIPInputStream(is)) {

            // 1. Limpar dados anteriores de forma eficiente
            snapshotFiles.deleteBySnapshotId(snapshotId);

            // 2. Parsing em streaming para evitar OOM e respeitar o Mandato 1
            JsonParser parser = mapper.getFactory().createParser(gis);
            
            List<FileChunk> chunkBatch = new ArrayList<>(250);
            int count = 0;

            while (parser.nextToken() != null) {
                if (parser.currentToken() == JsonToken.FIELD_NAME && "files".equals(parser.currentName())) {
                    parser.nextToken();
                    while (parser.nextToken() != JsonToken.END_ARRAY) {
                        parseFile(parser, snapshot, chunkBatch);
                        count++;
                    }
                }
            }
            
            if (!chunkBatch.isEmpty()) {
                saveChunkBatch(chunkBatch);
            }

            snapshot.status = SnapshotStatus.COMPLETED;
            snapshots.save(snapshot);
            log.info("Indexação concluída: {} arquivos processados para o snapshot: {}", count, snapshotId);

        } catch (Exception e) {
            log.error("Erro ao processar manifesto do snapshot: " + snapshotId, e);
            snapshot.status = SnapshotStatus.FAILED;
            snapshots.save(snapshot);
        }
    }

    private void parseFile(JsonParser parser, Snapshot snapshot, List<FileChunk> chunkBatch) throws java.io.IOException {
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
                        chunkBatch.add(parseChunk(parser, file));
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

    private void saveChunkBatch(List<FileChunk> batch) {
        fileChunks.saveAll(batch);
        entityManager.flush();
        entityManager.clear();
    }
}
