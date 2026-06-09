package com.keeply.agent.core;

import com.github.luben.zstd.ZstdOutputStream;
import com.keeply.agent.api.BackendClient;
import com.keeply.agent.model.TransferCredentials;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RestoreEngineTest {

    @TempDir
    Path tempDir;

    @Test
    void restoreFinishesTransferSessionAfterSuccessfulRestore() throws Exception {
        UUID snapshotId = UUID.randomUUID();
        UUID transferSessionId = UUID.randomUUID();
        TrackingRestoreBackend backend = new TrackingRestoreBackend(transferSessionId);
        byte[] manifest = zstd("""
                {
                  "manifestVersion": 2,
                  "sourcePath": "/original",
                  "chunkCompression": {"algorithm": "ZSTD", "level": 3},
                  "files": []
                }
                """);
        RestoreEngine restoreEngine = new RestoreEngine(backend,
                (ignoredBackend, ignoredSnapshotId, ignoredCredentials) -> new ManifestOnlyStorage(transferSessionId, manifest));

        restoreEngine.restore(snapshotId, tempDir);

        assertEquals(transferSessionId, backend.finishedTransferSessionId);
        assertEquals(null, backend.cancelledTransferSessionId);
    }

    @Test
    void restoreCancelsTransferSessionWhenManifestValidationFails() throws Exception {
        UUID snapshotId = UUID.randomUUID();
        UUID transferSessionId = UUID.randomUUID();
        TrackingRestoreBackend backend = new TrackingRestoreBackend(transferSessionId);
        byte[] manifest = zstd("""
                {
                  "manifestVersion": 1,
                  "sourcePath": "/original",
                  "chunkCompression": {"algorithm": "ZSTD", "level": 3},
                  "files": []
                }
                """);
        RestoreEngine restoreEngine = new RestoreEngine(backend,
                (ignoredBackend, ignoredSnapshotId, ignoredCredentials) -> new ManifestOnlyStorage(transferSessionId, manifest));

        assertThrows(IllegalStateException.class, () -> restoreEngine.restore(snapshotId, tempDir));

        assertEquals(null, backend.finishedTransferSessionId);
        assertEquals(transferSessionId, backend.cancelledTransferSessionId);
    }

    private static byte[] zstd(String json) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZstdOutputStream zstd = new ZstdOutputStream(bytes)) {
            zstd.write(json.getBytes(StandardCharsets.UTF_8));
        }
        return bytes.toByteArray();
    }

    private static final class TrackingRestoreBackend extends BackendClient {
        private final UUID transferSessionId;
        private UUID finishedTransferSessionId;
        private UUID cancelledTransferSessionId;

        private TrackingRestoreBackend(UUID transferSessionId) {
            super("http://localhost:8080");
            this.transferSessionId = transferSessionId;
        }

        @Override
        public TransferCredentials startRestoreSession(UUID snapshotId) {
            return new TransferCredentials(transferSessionId, "S3", "bucket", "endpoint", "key", "secret",
                    "token", null, null, "prefix");
        }

        @Override
        public void finishTransferSession(UUID transferSessionId) {
            this.finishedTransferSessionId = transferSessionId;
        }

        @Override
        public void cancelTransferSession(UUID transferSessionId) {
            this.cancelledTransferSessionId = transferSessionId;
        }
    }

    private record ManifestOnlyStorage(UUID sessionId, byte[] manifest) implements TransferObjectClient {
        @Override
        public void uploadChunk(String hash, Path chunkFile, ChunkCodec codec) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void uploadManifest(Path zstdFile) {
            throw new UnsupportedOperationException();
        }

        @Override
        public InputStream openManifest(UUID snapshotId) {
            return new ByteArrayInputStream(manifest);
        }

        @Override
        public InputStream openChunk(String hash, ChunkCodec codec) {
            throw new UnsupportedOperationException();
        }

        @Override
        public StoredObjectInfo statManifest(UUID snapshotId) {
            return new StoredObjectInfo(true, (long) manifest.length);
        }

        @Override
        public StoredObjectInfo statChunk(String hash, ChunkCodec codec) {
            throw new UnsupportedOperationException();
        }
    }
}
