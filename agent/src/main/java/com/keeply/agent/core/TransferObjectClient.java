package com.keeply.agent.core;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.UUID;

public interface TransferObjectClient {
    UUID sessionId();
    void uploadChunk(String hash, Path chunkFile, ChunkCodec codec);
    void uploadManifest(Path zstdFile);
    InputStream openManifest(UUID snapshotId);
    InputStream openChunk(String hash, ChunkCodec codec);
}
