package com.keeply.agent.core;

import com.keeply.agent.model.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;

public class ManifestBuilder {
    private final ContentDefinedChunker chunker = new ContentDefinedChunker();

    public BackupPlan build(String snapshotId, Path sourceRoot) {
        List<FileManifest> files = new ArrayList<>();
        List<ChunkPayload> allChunks = new ArrayList<>();

        for (Path file : FileScanner.scan(sourceRoot)) {
            try {
                Path relative = sourceRoot.relativize(file);
                String relativePath = relative.toString().replace("\\", "/");

                var chunkResult = chunker.chunk(file);
                allChunks.addAll(chunkResult.payloads());

                FileManifest fm = new FileManifest(
                        relativePath,
                        Files.size(file),
                        Files.getLastModifiedTime(file).toInstant(),
                        Sha256Hasher.hashFile(file),
                        chunkResult.manifestChunks()
                );
                files.add(fm);
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
