package com.keeply.backend.service;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.keeply.backend.dto.ManifestParsingDtos;
import com.keeply.backend.model.FileChunk;
import com.keeply.backend.model.Snapshot;
import com.keeply.backend.model.SnapshotFile;
import com.keeply.backend.model.SnapshotStatus;
import com.keeply.backend.repository.SnapshotFileRepository;
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
import java.util.zip.GZIPInputStream;

@Service
public class ManifestParserService {
    private static final Logger log = LoggerFactory.getLogger(ManifestParserService.class);
    private final SnapshotRepository snapshots;
    private final SnapshotFileRepository snapshotFiles;
    private final ObjectStorageService storage;
    private final ObjectMapper mapper;
    private final EntityManager entityManager;

    public ManifestParserService(SnapshotRepository snapshots,
                                 SnapshotFileRepository snapshotFiles,
                                 ObjectStorageService storage,
                                 ObjectMapper mapper,
                                 EntityManager entityManager) {
        this.snapshots = snapshots;
        this.snapshotFiles = snapshotFiles;
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
            
            List<SnapshotFile> batch = new ArrayList<>();
            int count = 0;

            while (parser.nextToken() != JsonToken.END_OBJECT) {
                String fieldName = parser.getCurrentName();
                if ("files".equals(fieldName)) {
                    parser.nextToken(); // Entra no array
                    while (parser.nextToken() != JsonToken.END_ARRAY) {
                        ManifestParsingDtos.FileManifest fm = mapper.readValue(parser, ManifestParsingDtos.FileManifest.class);
                        
                        SnapshotFile sf = new SnapshotFile();
                        sf.snapshot = snapshot;
                        sf.path = fm.path();
                        sf.size = fm.size();
                        sf.lastModified = fm.lastModified();
                        sf.sha256 = fm.sha256();
                        sf.chunks = new ArrayList<>();

                        for (ManifestParsingDtos.ManifestChunk mc : fm.chunks()) {
                            FileChunk fc = new FileChunk();
                            fc.snapshotFile = sf;
                            fc.chunkIndex = mc.index();
                            fc.chunkHash = mc.hash();
                            fc.originalSize = mc.originalSize();
                            fc.compressedSize = mc.compressedSize();
                            sf.chunks.add(fc);
                        }

                        batch.add(sf);
                        count++;

                        if (batch.size() >= 100) {
                            saveBatch(batch, snapshotId);
                            batch.clear();
                        }
                    }
                }
            }
            
            if (!batch.isEmpty()) {
                saveBatch(batch, snapshotId);
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

    private void saveBatch(List<SnapshotFile> batch, UUID snapshotId) {
        log.info("Salvando lote de {} arquivos para o snapshot: {}", batch.size(), snapshotId);
        snapshotFiles.saveAll(batch);
        // Limpa o persistence context para evitar lentidão progressiva (O(N^2) checks)
        entityManager.flush();
        entityManager.clear();
    }
}
