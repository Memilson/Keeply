package com.keeply.agent.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.keeply.agent.api.BackendClient;
import com.keeply.agent.model.BackupPlan;
import com.keeply.agent.model.ChunkPayload;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

public class BackupEngine {
    private final BackendClient backend;
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    public BackupEngine(BackendClient backend) {
        this.backend = backend;
    }

    public UUID backup(UUID deviceId, Path sourceRoot, Consumer<String> log) {
        UUID snapshotId = backend.startSnapshot(deviceId, sourceRoot.toAbsolutePath().toString());

        try {
            log.accept("Snapshot iniciado: " + snapshotId);

            BackupPlan plan = new ManifestBuilder().build(snapshotId.toString(), sourceRoot);
            List<String> hashes = plan.chunks().stream().map(ChunkPayload::hash).toList();
            Set<String> existing = backend.checkChunks(hashes);

            int sent = 0;
            for (ChunkPayload chunk : plan.chunks()) {
                if (!existing.contains(chunk.hash())) {
                    backend.uploadChunk(chunk);
                    sent++;
                }
            }

            String manifestJson = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(plan.manifest());
            backend.completeSnapshot(
                    snapshotId,
                    manifestJson,
                    plan.totalFiles(),
                    plan.totalOriginalSize(),
                    plan.totalCompressedSize()
            );

            log.accept("Backup concluído. Chunks novos enviados: " + sent);
            return snapshotId;
        } catch (Exception e) {
            backend.failSnapshot(snapshotId, e.getMessage());
            throw new IllegalStateException("Backup falhou", e);
        }
    }
}
