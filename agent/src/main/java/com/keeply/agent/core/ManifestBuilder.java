package com.keeply.agent.core;

import com.keeply.agent.model.*;

import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;

public class ManifestBuilder {
    private final ContentDefinedChunker chunker = new ContentDefinedChunker();
    private final LocalDatabase db;

    public ManifestBuilder(LocalDatabase db) {
        this.db = db;
    }

    public BackupPlan build(String snapshotId, Path sourceRoot) {
        List<FileManifest> files = new ArrayList<>();
        List<ChunkPayload> allChunks = new ArrayList<>();

        for (Path file : FileScanner.scan(sourceRoot)) {
            try {
                Path relative = sourceRoot.relativize(file);
                String relativePath = relative.toString().replace("\\", "/");
                
                long size = Files.size(file);
                long mtime = Files.getLastModifiedTime(file).toMillis();
                
                var chunkResult = chunker.chunk(file);
                allChunks.addAll(chunkResult.payloads());

                FileManifest fm = new FileManifest(
                        relativePath,
                        size,
                        Instant.ofEpochMilli(mtime),
                        Sha256Hasher.hashFile(file),
                        chunkResult.manifestChunks()
                );
                db.saveFileCache(relativePath, size, mtime, fm.sha256(), fm.chunks());
                files.add(fm);
            } catch (NoSuchFileException | AccessDeniedException e) {
                // Arquivos temporários podem desaparecer/mudar permissão durante o scan.
                // Nesses casos, ignoramos o arquivo e seguimos com o restante do backup.
                continue;
            } catch (IOException e) {
                // Erros de IO transitórios em arquivos individuais não devem abortar o snapshot inteiro.
                continue;
            } catch (Exception e) {
                throw new IllegalStateException("Falha ao gerar manifesto do arquivo: " + file, e);
            }
        }

        SnapshotManifest manifest = new SnapshotManifest(
                snapshotId,
                sourceRoot.toAbsolutePath().toString(),
                Instant.now(),
                "CONTENT_DEFINED_MIN_512KB_AVG_1MB_MAX_4MB",
                "GZIP",
                "SHA-256",
                files
        );

        return new BackupPlan(manifest, dedupePayloads(allChunks));
    }

    private List<ChunkPayload> dedupePayloads(List<ChunkPayload> chunks) {
        Map<String, ChunkPayload> unique = new LinkedHashMap<>();
        for (ChunkPayload c : chunks) {
            unique.putIfAbsent(c.hash(), c);
        }
        return new ArrayList<>(unique.values());
    }
}
