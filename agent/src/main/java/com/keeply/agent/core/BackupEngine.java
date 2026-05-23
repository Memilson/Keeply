package com.keeply.agent.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.keeply.agent.api.BackendClient;
import com.keeply.agent.model.BackupPlan;
import com.keeply.agent.model.ChunkPayload;
import com.keeply.agent.model.SnapshotSummary;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class BackupEngine {
    private final BackendClient backend;
    private final LocalDatabase db;
    private final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .findAndRegisterModules();

    public BackupEngine(BackendClient backend, LocalDatabase db) {
        this.backend = backend;
        this.db = db;
    }

    public UUID backup(UUID deviceId, Path sourceRoot, Consumer<String> log) {
        autoSyncCache(deviceId, sourceRoot, log);

        UUID snapshotId = backend.startSnapshot(deviceId, sourceRoot.toAbsolutePath().toString());

        try {
            log.accept("Snapshot iniciado: " + snapshotId);

            BackupPlan plan = new ManifestBuilder(db).build(snapshotId.toString(), sourceRoot);
            
            Set<String> knownLocally = db.getKnownChunks();
            List<String> toCheck = plan.chunks().stream()
                    .map(ChunkPayload::hash)
                    .filter(h -> !knownLocally.contains(h))
                    .collect(Collectors.toList());
            
            if (!toCheck.isEmpty()) {
                log.accept("Verificando " + toCheck.size() + " novos chunks no servidor...");
                Set<String> existsOnServer = backend.checkChunks(toCheck);
                db.addKnownChunks(existsOnServer);
                knownLocally.addAll(existsOnServer);
            }

            int sent = 0;
            for (ChunkPayload chunk : plan.chunks()) {
                if (!knownLocally.contains(chunk.hash())) {
                    backend.uploadChunk(chunk);
                    db.addKnownChunks(Set.of(chunk.hash()));
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

            db.setLastSyncedSnapshot(deviceId, sourceRoot.toAbsolutePath().toString(), snapshotId.toString());
            log.accept("Backup concluído. Chunks novos enviados: " + sent);
            return snapshotId;
        } catch (Exception e) {
            backend.failSnapshot(snapshotId, e.getMessage());
            throw new IllegalStateException("Backup falhou", e);
        }
    }

    private void autoSyncCache(UUID deviceId, Path sourceRoot, Consumer<String> log) {
        try {
            String pathStr = sourceRoot.toAbsolutePath().toString();
            List<SnapshotSummary> snapshots = backend.listSnapshots();
            
            SnapshotSummary latest = snapshots.stream()
                    .filter(s -> s.deviceId().equals(deviceId))
                    .filter(s -> s.sourcePath().equals(pathStr))
                    .filter(s -> "COMPLETED".equals(s.status()))
                    .findFirst()
                    .orElse(null);

            if (latest != null) {
                String lastSynced = db.getLastSyncedSnapshot(deviceId, pathStr);
                if (!latest.id().toString().equals(lastSynced)) {
                    log.accept("☁️ Nova versão de backup detectada na nuvem. Sincronizando memória local...");
                    String json = backend.downloadManifest(latest.id());
                    com.keeply.agent.model.SnapshotManifest manifest = mapper.readValue(json, com.keeply.agent.model.SnapshotManifest.class);
                    db.reconstructIndex(manifest);
                    db.setLastSyncedSnapshot(deviceId, pathStr, latest.id().toString());
                    log.accept("✅ Memória local atualizada.");
                }
            }
        } catch (Exception e) {
            log.accept("⚠️ Aviso: Não foi possível sincronizar o cache da nuvem automaticamente: " + e.getMessage());
        }
    }
}
