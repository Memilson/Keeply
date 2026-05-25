package com.keeply.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.keeply.backend.dto.ManifestParsingDtos;
import com.keeply.backend.model.FileChunk;
import com.keeply.backend.model.Snapshot;
import com.keeply.backend.model.SnapshotFile;
import com.keeply.backend.model.SnapshotStatus;
import com.keeply.backend.repository.FileChunkRepository;
import com.keeply.backend.repository.SnapshotFileRepository;
import com.keeply.backend.repository.SnapshotRepository;
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
    private final FileChunkRepository fileChunks;
    private final ObjectStorageService storage;
    private final ObjectMapper mapper;

    public ManifestParserService(SnapshotRepository snapshots,
                                 SnapshotFileRepository snapshotFiles,
                                 FileChunkRepository fileChunks,
                                 ObjectStorageService storage,
                                 ObjectMapper mapper) {
        this.snapshots = snapshots;
        this.snapshotFiles = snapshotFiles;
        this.fileChunks = fileChunks;
        this.storage = storage;
        this.mapper = mapper;
    }

    @Async
    @Transactional
    public void parseAsync(UUID snapshotId) {
        log.info("Iniciando indexação do manifesto para o snapshot: {}", snapshotId);
        Snapshot snapshot = snapshots.findById(snapshotId)
                .orElseThrow(() -> new IllegalArgumentException("Snapshot não encontrado: " + snapshotId));

        try (InputStream is = storage.getStream(snapshot.manifestKey);
             GZIPInputStream gis = new GZIPInputStream(is)) {

            ManifestParsingDtos.SnapshotManifest manifest = mapper.readValue(gis, ManifestParsingDtos.SnapshotManifest.class);

            // 1. Limpar dados anteriores de forma eficiente (Cascade no DB cuida dos chunks)
            snapshotFiles.deleteBySnapshotId(snapshotId);

            List<SnapshotFile> filesToSave = new ArrayList<>();
            List<FileChunk> chunksToSave = new ArrayList<>();

            for (ManifestParsingDtos.FileManifest fm : manifest.files()) {
                SnapshotFile sf = new SnapshotFile();
                sf.snapshot = snapshot;
                sf.path = fm.path();
                sf.size = fm.size();
                sf.lastModified = fm.lastModified();
                sf.sha256 = fm.sha256();
                
                // Precisamos salvar o arquivo antes para ter o ID para os chunks (ou usar Cascade)
                // Para simplificar e garantir batching, vamos salvar em lotes de 100
                SnapshotFile savedFile = snapshotFiles.save(sf);

                for (ManifestParsingDtos.ManifestChunk mc : fm.chunks()) {
                    FileChunk fc = new FileChunk();
                    fc.snapshotFile = savedFile;
                    fc.chunkIndex = mc.index();
                    fc.chunkHash = mc.hash();
                    fc.originalSize = mc.originalSize();
                    fc.compressedSize = mc.compressedSize();
                    chunksToSave.add(fc);
                    
                    if (chunksToSave.size() >= 1000) {
                        fileChunks.saveAll(chunksToSave);
                        chunksToSave.clear();
                    }
                }
            }
            
            if (!chunksToSave.isEmpty()) {
                fileChunks.saveAll(chunksToSave);
            }

            snapshot.status = SnapshotStatus.COMPLETED;
            snapshots.save(snapshot);
            log.info("Indexação do manifesto concluída com sucesso para o snapshot: {}", snapshotId);

        } catch (Exception e) {
            log.error("Erro ao processar manifesto do snapshot: " + snapshotId, e);
            snapshot.status = SnapshotStatus.FAILED;
            snapshots.save(snapshot);
        }
    }
}
