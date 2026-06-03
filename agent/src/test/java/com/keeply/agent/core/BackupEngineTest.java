package com.keeply.agent.core;

import com.keeply.agent.api.BackendClient;
import com.keeply.agent.model.StartedSnapshot;
import com.keeply.agent.model.SnapshotSummary;
import com.keeply.agent.model.TransferCredentials;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BackupEngineTest {

    @TempDir
    Path tempDir;

    @Test
    void backupFailsIfSourceRootIsInvalid() {
        BackendClient backend = new BackendClient("http://localhost:8080", null) {
            @Override
            public List<SnapshotSummary> listSnapshots() {
                return Collections.emptyList();
            }
            @Override
            public StartedSnapshot startSnapshot(UUID deviceId, String path) {
                return new StartedSnapshot(
                    new SnapshotSummary(UUID.randomUUID(), deviceId, "IN_PROGRESS", path, 0L, 0L, 0L, null, null, null),
                    new TransferCredentials(UUID.randomUUID(), "S3", "bucket", "endpoint", "key", "secret", "token", null, null, "prefix")
                );
            }
        };

        BackupEngine backupEngine = new BackupEngine(backend, null);
        UUID deviceId = UUID.randomUUID();
        Path source = tempDir.resolve("non_existent_folder");

        assertThrows(Exception.class, () -> {
            backupEngine.backup(deviceId, source);
        });
    }

    @Test
    void backupCancelsTransferSessionWhenStrictSnapshotFinalizationFails() throws Exception {
        UUID deviceId = UUID.randomUUID();
        UUID snapshotId = UUID.randomUUID();
        UUID transferSessionId = UUID.randomUUID();
        Path source = Files.createDirectory(tempDir.resolve("source"));
        TrackingBackendClient backend = new TrackingBackendClient(snapshotId, transferSessionId);

        try (FailingManifestDatabase db = new FailingManifestDatabase(tempDir.resolve("agent.sqlite"))) {
            BackupEngine backupEngine = new BackupEngine(backend, db);

            BackupSnapshotException error = assertThrows(BackupSnapshotException.class, () ->
                    backupEngine.backup(deviceId, source));

            assertEquals(snapshotId, error.snapshotId());
            assertEquals(transferSessionId, error.transferSessionId());
            assertEquals(transferSessionId, backend.cancelledTransferSessionId);
        }
    }

    private static final class TrackingBackendClient extends BackendClient {
        private final UUID snapshotId;
        private final UUID transferSessionId;
        private UUID cancelledTransferSessionId;

        private TrackingBackendClient(UUID snapshotId, UUID transferSessionId) {
            super("http://localhost:8080", null);
            this.snapshotId = snapshotId;
            this.transferSessionId = transferSessionId;
        }

        @Override
        public List<SnapshotSummary> listSnapshots() {
            return Collections.emptyList();
        }

        @Override
        public StartedSnapshot startSnapshot(UUID deviceId, String path) {
            return new StartedSnapshot(
                    new SnapshotSummary(snapshotId, deviceId, "IN_PROGRESS", path, 0L, 0L, 0L, null, null, null),
                    new TransferCredentials(transferSessionId, "S3", "bucket", "endpoint", "key", "secret",
                            "token", null, null, "prefix")
            );
        }

        @Override
        public void cancelTransferSession(UUID transferSessionId) {
            this.cancelledTransferSessionId = transferSessionId;
        }
    }

    private static final class FailingManifestDatabase extends LocalDatabase {
        private FailingManifestDatabase(Path path) {
            super(path.toString());
        }

        @Override
        public synchronized void writeManifestZstd(Path output, String snapshotId, String sourcePath) {
            throw new IllegalStateException("manifest write failed");
        }
    }
}
