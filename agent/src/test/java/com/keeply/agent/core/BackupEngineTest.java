package com.keeply.agent.core;

import com.keeply.agent.api.BackendClient;
import com.keeply.agent.model.StartedSnapshot;
import com.keeply.agent.model.SnapshotSummary;
import com.keeply.agent.model.TransferCredentials;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

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
}
