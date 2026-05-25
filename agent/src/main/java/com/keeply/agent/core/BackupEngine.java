package com.keeply.agent.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.keeply.agent.api.BackendClient;
import com.keeply.agent.model.ChunkPayload;
import com.keeply.agent.model.FileManifest;
import com.keeply.agent.model.ManifestChunk;
import com.keeply.agent.model.SnapshotManifest;
import com.keeply.agent.model.SnapshotSummary;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

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
        long startTotal = System.nanoTime();
        String sourcePath = sourceRoot.toAbsolutePath().normalize().toString();
        autoSyncCache(deviceId, sourceRoot, log);

        UUID snapshotId = backend.startSnapshot(deviceId, sourcePath);

        try {
            log.accept("Snapshot iniciado: " + snapshotId);

            Set<String> locallyKnown = db.getKnownChunks();
            Set<String> verifiedRemote = ConcurrentHashMap.newKeySet();

            if (!locallyKnown.isEmpty()) {
                log.accept("Verificando integridade de " + locallyKnown.size() + " chunks no servidor...");
                List<String> hashes = new ArrayList<>(locallyKnown);
                // Dividir em lotes de 1000 para evitar requests gigantes
                for (int i = 0; i < hashes.size(); i += 1000) {
                    List<String> batch = hashes.subList(i, Math.min(i + 1000, hashes.size()));
                    Set<String> existing = backend.checkChunks(batch);
                    verifiedRemote.addAll(existing);
                }
                log.accept("Integridade confirmada: " + verifiedRemote.size() + "/" + locallyKnown.size() + " chunks válidos.");
                
                // Limpar do banco local o que não existe mais no servidor
                locallyKnown.removeAll(verifiedRemote);
                if (!locallyKnown.isEmpty()) {
                    log.accept("⚠️ Removendo " + locallyKnown.size() + " chunks órfãos do cache local.");
                    db.removeKnownChunks(locallyKnown);
                }
            }

            Set<String> knownInSession = ConcurrentHashMap.newKeySet();
            knownInSession.addAll(verifiedRemote);

            // Controle de hashes únicos para cálculo do tamanho comprimido total
            Set<String> uniqueHashesInManifest = ConcurrentHashMap.newKeySet();
            AtomicLong totalCompressedSize = new AtomicLong(0);

            AtomicInteger sentCount = new AtomicInteger(0);
            AtomicInteger totalFiles = new AtomicInteger(0);
            AtomicInteger filesCached = new AtomicInteger(0);
            AtomicInteger chunksGenerated = new AtomicInteger(0);
            AtomicInteger chunksReused = new AtomicInteger(0);
            AtomicLong totalOriginalSize = new AtomicLong(0);
            ContentDefinedChunker chunker = new ContentDefinedChunker();

            // A fila do próprio executor também precisa ser limitada: cada tarefa retém um chunk bruto.
            java.util.concurrent.ThreadPoolExecutor uploaderPool = new java.util.concurrent.ThreadPoolExecutor(
                    4,
                    4,
                    0L,
                    java.util.concurrent.TimeUnit.MILLISECONDS,
                    new java.util.concurrent.ArrayBlockingQueue<>(4),
                    new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy()
            );
            java.util.concurrent.ConcurrentLinkedQueue<Exception> uploadErrors =
                    new java.util.concurrent.ConcurrentLinkedQueue<>();

            db.clearBackupManifest();

            long startProcessing = System.nanoTime();
            try (var stream = FileScanner.scan(sourceRoot)) {
                stream.forEach(file -> {
                    String relativePath = sourceRoot.relativize(file).toString().replace("\\", "/");
                    try {
                        int currentTotal = totalFiles.incrementAndGet();
                        if (currentTotal % 1000 == 0) {
                            log.accept(String.format("Progresso: %d arquivos escaneados...", currentTotal));
                        }

                        long size = Files.size(file);
                        long mtime = Files.getLastModifiedTime(file).toMillis();
                        totalOriginalSize.addAndGet(size);

                        LocalDatabase.CachedFile cached = db.getFileCache(sourcePath, relativePath);
                        
                        boolean cacheValid = cached != null && cached.size() == size && cached.lastModified() == mtime;
                        if (cacheValid) {
                            for (ManifestChunk c : cached.chunks()) {
                                if (!knownInSession.contains(c.hash())) {
                                    cacheValid = false;
                                    break;
                                }
                            }
                        }

                        if (cacheValid) {
                            filesCached.incrementAndGet();
                            chunksReused.addAndGet(cached.chunks().size());
                            db.addManifestFile(relativePath, size, mtime, cached.hash());
                            for (ManifestChunk c : cached.chunks()) {
                                db.addManifestChunk(relativePath, c.index(), c.hash(), c.originalSize(), c.compressedSize());
                                if (uniqueHashesInManifest.add(c.hash())) {
                                    totalCompressedSize.addAndGet(c.compressedSize());
                                }
                            }
                        } else {
                            String fileHash = chunker.process(file, chunkData -> {
                                byte[] raw = chunkData.data();
                                String chunkHash = Sha256Hasher.hashBytes(raw);
                                int originalSize = raw.length;
                                
                                if (knownInSession.add(chunkHash)) {
                                    uploaderPool.execute(() -> {
                                        try {
                                            byte[] compressed = GzipCompressor.compress(raw);
                                            ChunkPayload payload = new ChunkPayload(chunkHash, originalSize, compressed.length, compressed);
                                            backend.uploadChunk(payload);
                                            db.addKnownChunks(Set.of(chunkHash));
                                            sentCount.incrementAndGet();
                                            db.addManifestChunk(relativePath, chunkData.index(), chunkHash, originalSize, compressed.length);
                                            if (uniqueHashesInManifest.add(chunkHash)) {
                                                totalCompressedSize.addAndGet(compressed.length);
                                            }
                                        } catch (Exception e) {
                                            uploadErrors.add(e);
                                            log.accept("Erro no upload do chunk " + chunkHash + ": " + e.getMessage());
                                        }
                                    });
                                } else {
                                    chunksReused.incrementAndGet();
                                    byte[] compressed = GzipCompressor.compress(raw);
                                    db.addManifestChunk(relativePath, chunkData.index(), chunkHash, originalSize, compressed.length);
                                    if (uniqueHashesInManifest.add(chunkHash)) {
                                        totalCompressedSize.addAndGet(compressed.length);
                                    }
                                }
                            });

                            db.addManifestFile(relativePath, size, mtime, fileHash);
                            db.saveFileCache(sourcePath, relativePath, size, mtime, fileHash, null); // Nota: chunks_json será preenchido no final se necessário, ou podemos mudar o cache para usar as novas tabelas
                            chunksGenerated.incrementAndGet(); // Incrementamos por arquivo modificado
                        }
                    } catch (java.nio.file.NoSuchFileException e) {
                        log.accept("⚠️ Arquivo ignorado (removido durante o scan): " + relativePath);
                    } catch (Exception e) {
                        log.accept("❌ Erro ao processar arquivo " + relativePath + ": " + e.getMessage());
                    }
                });
            }
            
            // Finaliza o processamento em background
            uploaderPool.shutdown();
            while (!uploaderPool.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)) {
                log.accept("Aguardando finalização dos uploads pendentes...");
            }
            if (!uploadErrors.isEmpty()) {
                throw new IllegalStateException("Falha ao enviar um ou mais chunks", uploadErrors.peek());
            }

            double processDuration = (System.nanoTime() - startProcessing) / 1_000_000_000.0;

            log.accept(String.format("[PERF] files.total=%d files.cached=%d files.changed=%d",
                    totalFiles.get(), filesCached.get(), totalFiles.get() - filesCached.get()));
            log.accept(String.format("[PERF] chunks.generated=%d chunks.reused=%d chunks.uploaded=%d",
                    chunksGenerated.get(), chunksReused.get(), sentCount.get()));
            log.accept(String.format("[PERF] time.processing=%.2fs", processDuration));

            long startManifest = System.nanoTime();

            // Reconstruímos a lista apenas para a serialização final (JSON).
            // Para 175k arquivos, isso ocupará memória temporariamente, mas é muito menos que manter
            // os objetos durante todo o scan concorrente com o uploadQueue cheio.
            List<FileManifest> finalManifestFiles;
            try (var manifestStream = db.getManifestFilesStream()) {
                finalManifestFiles = manifestStream.toList();
            }

            SnapshotManifest manifest = new SnapshotManifest(
                    snapshotId.toString(),
                    sourcePath,
                    Instant.now(),
                    "CONTENT_DEFINED_MIN_512KB_AVG_1MB_MAX_4MB",
                    "GZIP",
                    "SHA-256",
                    finalManifestFiles
            );

            String manifestJson = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(manifest);

            backend.completeSnapshot(
                    snapshotId,
                    manifestJson,
                    totalFiles.get(),
                    totalOriginalSize.get(),
                    totalCompressedSize.get()
            );

            // Atualiza o cache de arquivos para o próximo backup incremental
            for (FileManifest f : finalManifestFiles) {
                db.saveFileCache(sourcePath, f.path(), f.size(), f.lastModified().toEpochMilli(), f.sha256(), f.chunks());
            }

            double manifestDuration = (System.nanoTime() - startManifest) / 1_000_000_000.0;
            log.accept(String.format("[PERF] manifest.files=%d duration=%.2fs", totalFiles.get(), manifestDuration));

            db.setLastSyncedSnapshot(deviceId, sourcePath, snapshotId.toString());
            db.clearBackupManifest(); // Limpa as tabelas temporárias

            double totalDuration = (System.nanoTime() - startTotal) / 1_000_000_000.0;
            log.accept(String.format("[PERF] total.duration=%.2fs chunks.sent=%d", totalDuration, sentCount.get()));

            log.accept("Backup concluído com sucesso.");
            return snapshotId;
        } catch (Exception e) {
            backend.failSnapshot(snapshotId, e.getMessage());
            throw new IllegalStateException("Backup falhou", e);
        }
    }

    private void autoSyncCache(UUID deviceId, Path sourceRoot, Consumer<String> log) {
        try {
            String pathStr = sourceRoot.toAbsolutePath().normalize().toString();
            List<SnapshotSummary> snapshots = backend.listSnapshots();
            
            List<SnapshotSummary> sourceSnapshots = snapshots.stream()
                    .filter(s -> s.deviceId().equals(deviceId))
                    .filter(s -> s.sourcePath().equals(pathStr))
                    .filter(s -> "COMPLETED".equals(s.status()))
                    .toList();

            if (sourceSnapshots.isEmpty()) {
                // Se o servidor não tem nenhum snapshot para esta origem, mas o banco local tem cache,
                // significa que o servidor foi resetado. Limpamos o cache local para evitar "ghost chunks".
                log.accept("ℹ️ Servidor sem histórico para esta origem. Limpando cache local para sincronização total.");
                db.clearCacheForPath(pathStr);
                return;
            }

            SnapshotSummary latest = sourceSnapshots.get(0);
            String lastSynced = db.getLastSyncedSnapshot(deviceId, pathStr);
            if (!latest.id().toString().equals(lastSynced)) {
                log.accept("☁️ Nova versão de backup detectada na nuvem. Sincronizando memória local...");
                String json = backend.downloadManifest(latest.id());
                com.keeply.agent.model.SnapshotManifest manifest = mapper.readValue(json, com.keeply.agent.model.SnapshotManifest.class);
                db.reconstructIndex(pathStr, manifest);
                db.setLastSyncedSnapshot(deviceId, pathStr, latest.id().toString());
                log.accept("✅ Memória local atualizada.");
            }
        } catch (Exception e) {
            log.accept("⚠️ Aviso: Não foi possível sincronizar o cache da nuvem automaticamente: " + e.getMessage());
        }
    }
}
