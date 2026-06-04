package com.keeply.backend.service;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.luben.zstd.ZstdInputStream;
import com.keeply.backend.model.FileChunk;
import com.keeply.backend.model.Snapshot;
import com.keeply.backend.model.SnapshotFile;
import com.keeply.backend.repository.FileChunkRepository;
import com.keeply.backend.repository.SnapshotFileRepository;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ManifestParserService {
    private final SnapshotFileRepository snapshotFiles;
    private final FileChunkRepository fileChunks;
    private final ObjectStorageService storage;
    private final ObjectMapper mapper;

    public ManifestParserService(SnapshotFileRepository snapshotFiles,
                                 FileChunkRepository fileChunks,
                                 ObjectStorageService storage,
                                 ObjectMapper mapper) {
        this.snapshotFiles = snapshotFiles;
        this.fileChunks = fileChunks;
        this.storage = storage;
        this.mapper = mapper;
    }

    public ParsedManifest parseAndPersist(String manifestKey, Snapshot snapshot) throws java.io.IOException {
        Map<String, ChunkReference> references = new LinkedHashMap<>();
        int count = 0;
        try (InputStream is = storage.getStream(manifestKey);
             ZstdInputStream zstd = new ZstdInputStream(is);
             JsonParser parser = mapper.getFactory().createParser(zstd)) {
            snapshotFiles.deleteBySnapshotId(snapshot.id);
            List<FileChunk> chunkBatch = new ArrayList<>(250);
            Integer manifestVersion = null;
            ChunkEncoding chunkEncoding = null;

            while (parser.nextToken() != null) {
                if (parser.currentToken() == JsonToken.FIELD_NAME && "manifestVersion".equals(parser.currentName())) {
                    parser.nextToken();
                    manifestVersion = parser.getIntValue();
                } else if (parser.currentToken() == JsonToken.FIELD_NAME && "chunkCompression".equals(parser.currentName())) {
                    chunkEncoding = parseChunkEncoding(parser);
                } else if (parser.currentToken() == JsonToken.FIELD_NAME && "files".equals(parser.currentName())) {
                    requireManifestVersion(manifestVersion);
                    requireZstdLevel3(chunkEncoding);
                    parser.nextToken();
                    while (parser.nextToken() != JsonToken.END_ARRAY) {
                        parseFile(parser, snapshot, references, chunkBatch, chunkEncoding);
                        count++;
                    }
                }
            }
            requireManifestVersion(manifestVersion);
            requireZstdLevel3(chunkEncoding);

            if (!chunkBatch.isEmpty()) {
                fileChunks.saveAll(chunkBatch);
            }
        }
        return new ParsedManifest(count, references);
    }

    private void parseFile(JsonParser parser, Snapshot snapshot, Map<String, ChunkReference> references,
                           List<FileChunk> chunkBatch, ChunkEncoding chunkEncoding) throws java.io.IOException {
        SnapshotFile file = new SnapshotFile();
        file.snapshot = snapshot;
        Map<Integer, FileChunk> chunksByIndex = new java.util.HashMap<>();
        while (parser.nextToken() != JsonToken.END_OBJECT) {
            if (parser.currentToken() != JsonToken.FIELD_NAME) {
                continue;
            }
            String name = parser.currentName();
            parser.nextToken();
            switch (name) {
                case "path" -> file.path = parser.getValueAsString();
                case "size" -> file.size = parser.getLongValue();
                case "lastModified" -> file.lastModified = Instant.parse(parser.getValueAsString());
                case "sha256" -> file.sha256 = parser.getValueAsString();
                case "chunks" -> {
                    snapshotFiles.save(file);
                    while (parser.nextToken() != JsonToken.END_ARRAY) {
                        FileChunk chunk = parseChunk(parser, file, chunkEncoding);
                        FileChunk previous = chunksByIndex.putIfAbsent(chunk.chunkIndex, chunk);
                        if (previous != null) {
                            if (isEquivalentChunk(previous, chunk)) {
                                continue;
                            }
                            throw new IllegalStateException("Manifesto inválido: chunk duplicado com conteúdo divergente em "
                                    + file.path + " index=" + chunk.chunkIndex);
                        }
                        references.putIfAbsent(chunk.chunkHash.toLowerCase(java.util.Locale.ROOT),
                                new ChunkReference(chunk.chunkHash.toLowerCase(java.util.Locale.ROOT),
                                        chunk.originalSize, chunk.compressedSize,
                                        chunk.compressionAlgorithm, chunk.compressionLevel));
                        chunkBatch.add(chunk);
                        if (chunkBatch.size() >= 250) {
                            fileChunks.saveAll(chunkBatch);
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

    private boolean isEquivalentChunk(FileChunk left, FileChunk right) {
        return left.chunkHash.equalsIgnoreCase(right.chunkHash)
                && left.originalSize == right.originalSize
                && left.compressedSize == right.compressedSize;
    }

    private FileChunk parseChunk(JsonParser parser, SnapshotFile file, ChunkEncoding chunkEncoding) throws java.io.IOException {
        FileChunk chunk = new FileChunk();
        chunk.snapshotFile = file;
        chunk.compressionAlgorithm = chunkEncoding.algorithm();
        chunk.compressionLevel = chunkEncoding.level();
        while (parser.nextToken() != JsonToken.END_OBJECT) {
            if (parser.currentToken() != JsonToken.FIELD_NAME) {
                continue;
            }
            String name = parser.currentName();
            parser.nextToken();
            switch (name) {
                case "index" -> chunk.chunkIndex = parser.getIntValue();
                case "hash" -> chunk.chunkHash = parser.getValueAsString();
                case "originalSize" -> chunk.originalSize = parser.getLongValue();
                case "storedSize" -> chunk.compressedSize = parser.getLongValue();
                default -> parser.skipChildren();
            }
        }
        return chunk;
    }

    private ChunkEncoding parseChunkEncoding(JsonParser parser) throws java.io.IOException {
        String algorithm = null;
        Integer level = null;
        parser.nextToken();
        while (parser.nextToken() != JsonToken.END_OBJECT) {
            if (parser.currentToken() != JsonToken.FIELD_NAME) {
                continue;
            }
            String name = parser.currentName();
            parser.nextToken();
            switch (name) {
                case "algorithm" -> algorithm = parser.getValueAsString();
                case "level" -> level = parser.currentToken() == JsonToken.VALUE_NULL ? null : parser.getIntValue();
                default -> parser.skipChildren();
            }
        }
        ChunkEncoding encoding = new ChunkEncoding(algorithm, level);
        requireZstdLevel3(encoding);
        return encoding;
    }

    private void requireManifestVersion(Integer manifestVersion) {
        if (manifestVersion == null || manifestVersion != 2) {
            throw new IllegalStateException("Manifesto deve declarar manifestVersion=2");
        }
    }

    private void requireZstdLevel3(ChunkEncoding encoding) {
        if (encoding == null || !"ZSTD".equalsIgnoreCase(encoding.algorithm())
                || encoding.level() == null || encoding.level() != 3) {
            throw new IllegalStateException("Manifesto deve declarar chunkCompression ZSTD level 3");
        }
    }

    public record ParsedManifest(int fileCount, Map<String, ChunkReference> references) {
    }

    public record ChunkReference(String hash, long originalSize, long compressedSize,
                                 String compressionAlgorithm, Integer compressionLevel) {
    }

    private record ChunkEncoding(String algorithm, Integer level) {
    }
}
