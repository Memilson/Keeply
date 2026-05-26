package com.keeply.agent.core;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.UUID;

public interface TransferObjectClient {
    UUID sessionId();
    void uploadChunk(String hash, Path gzipFile);
    void uploadManifest(Path gzipFile);
    InputStream openManifest(UUID snapshotId);
    InputStream openChunk(String hash);
}
