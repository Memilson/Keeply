package com.keeply.agent.core;

import com.keeply.agent.api.BackendClient;
import com.keeply.agent.model.ChunkMetadata;
import com.keeply.agent.model.SnapshotSummary;
import com.keeply.agent.model.StartedSnapshot;
import com.keeply.agent.model.TransferCredentials;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.Assumptions;
import org.mockito.MockedConstruction;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.when;

class BackupEngineTest {

    @TempDir
    Path tempDir;

    @Test
    void backupFailsIfSourceRootIsInvalid() {
        TrackingBackendClient backend = new TrackingBackendClient();
        BackupEngine backupEngine = new BackupEngine(backend, new CachedOnlyDatabase(tempDir.resolve("agent.sqlite")));
        UUID deviceId = UUID.randomUUID();
        Path source = tempDir.resolve("non_existent_folder");

        assertThrows(Exception.class, () -> backupEngine.backup(deviceId, source));
        assertEquals(0, backend.startSnapshotCalls);
    }

    @Test
    void backupUsesRealFileProgressAndReservesFinalStages() throws Exception {
        Path source = Files.createDirectory(tempDir.resolve("source"));
        Files.writeString(source.resolve("a.txt"), "alpha");
        Files.writeString(source.resolve("b.txt"), "beta");
        Files.writeString(source.resolve("c.txt"), "gamma");

        TrackingBackendClient backend = new TrackingBackendClient();
        List<BackupProgressListener.BackupProgress> progressEvents = new ArrayList<>();

        try (CachedOnlyDatabase db = new CachedOnlyDatabase(tempDir.resolve("progress.sqlite"));
             MockedConstruction<DirectTransferStorage> ignored = mockTransferStorage(backend.transferSessionId)) {
            BackupEngine backupEngine = new BackupEngine(backend, db, progressEvents::add);

            UUID snapshotId = backupEngine.backup(UUID.randomUUID(), source);

            assertEquals(backend.snapshotId, snapshotId);
        }

        List<Integer> percents = progressEvents.stream().map(BackupProgressListener.BackupProgress::percent).toList();
        List<String> messages = progressEvents.stream().map(BackupProgressListener.BackupProgress::message).toList();

        assertEquals("Preparando backup", messages.get(0));
        assertEquals("Escaneando arquivos", messages.get(1));
        assertEquals("Processando arquivos (0 / 3)", messages.get(2));
        assertMonotonic(percents);
        assertEquals(List.of(2, 2, 32, 32, 63, 63, 94), processingPercents(progressEvents));
        assertFalse(percents.contains(95) && percents.indexOf(95) < percents.lastIndexOf(94));
        assertTrue(messages.subList(3, 9).stream().anyMatch(message -> message.equals("Processando arquivos (0 / 3) - atual: a.txt")
                || message.equals("Processando arquivos (0 / 3) - atual: b.txt")
                || message.equals("Processando arquivos (0 / 3) - atual: c.txt")));
        assertTrue(messages.subList(3, 9).stream().anyMatch(message -> message.startsWith("Processando arquivos (0 / 3) - atual: ")));
        assertTrue(messages.subList(3, 9).stream().anyMatch(message -> message.startsWith("Processando arquivos (1 / 3) - atual: ")));
        assertTrue(messages.subList(3, 9).stream().anyMatch(message -> message.startsWith("Processando arquivos (2 / 3) - atual: ")));
        assertTrue(messages.subList(3, 9).stream().anyMatch(message -> message.startsWith("Processando arquivos (3 / 3) - atual: ")));
        assertEquals(List.of(0, 1, 2, 2, 32, 32, 63, 63, 94, 95, 97, 100), percents);
        assertEquals("Enviando manifesto", messages.get(9));
        assertEquals("Concluindo snapshot", messages.get(10));
        assertEquals("Backup concluído", messages.get(11));
    }

    @Test
    void backupForEmptyDirectoryStillAdvancesThroughFinalStages() throws Exception {
        Path source = Files.createDirectory(tempDir.resolve("empty-source"));
        TrackingBackendClient backend = new TrackingBackendClient();
        List<BackupProgressListener.BackupProgress> progressEvents = new ArrayList<>();

        try (CachedOnlyDatabase db = new CachedOnlyDatabase(tempDir.resolve("empty.sqlite"));
             MockedConstruction<DirectTransferStorage> ignored = mockTransferStorage(backend.transferSessionId)) {
            BackupEngine backupEngine = new BackupEngine(backend, db, progressEvents::add);
            backupEngine.backup(UUID.randomUUID(), source);
        }

        assertEquals(
                List.of(0, 1, 2, 95, 97, 100),
                progressEvents.stream().map(BackupProgressListener.BackupProgress::percent).toList()
        );
        assertEquals(
                List.of(
                        "Preparando backup",
                        "Escaneando arquivos",
                        "Processando arquivos (0 / 0)",
                        "Enviando manifesto",
                        "Concluindo snapshot",
                        "Backup concluído"
                ),
                progressEvents.stream().map(BackupProgressListener.BackupProgress::message).toList()
        );
    }

    @Test
    void backupDoesNotOpenSnapshotWhenInitialScanFindsUnreadableEntries() throws Exception {
        Path source = Files.createDirectory(tempDir.resolve("unreadable-source"));
        Path unreadableFile = Files.writeString(source.resolve("blocked.txt"), "blocked");
        Assumptions.assumeTrue(Files.getFileStore(unreadableFile).supportsFileAttributeView("posix"));
        Set<PosixFilePermission> originalPermissions = Files.getPosixFilePermissions(unreadableFile);
        Files.setPosixFilePermissions(unreadableFile, EnumSet.noneOf(PosixFilePermission.class));

        TrackingBackendClient backend = new TrackingBackendClient();
        List<BackupProgressListener.BackupProgress> progressEvents = new ArrayList<>();

        try (CachedOnlyDatabase db = new CachedOnlyDatabase(tempDir.resolve("scan-failure.sqlite"))) {
            BackupEngine backupEngine = new BackupEngine(backend, db, progressEvents::add);

            IllegalStateException error = assertThrows(IllegalStateException.class,
                    () -> backupEngine.backup(UUID.randomUUID(), source));

            assertTrue(error.getMessage().contains("Falha ao escanear arquivos antes de iniciar o snapshot"));
            assertEquals(0, backend.startSnapshotCalls);
            assertEquals(List.of(0, 1),
                    progressEvents.stream().map(BackupProgressListener.BackupProgress::percent).toList());
        } finally {
            Files.setPosixFilePermissions(unreadableFile, originalPermissions);
        }
    }

    @Test
    void backupCancelsTransferSessionWhenStrictSnapshotFinalizationFails() throws Exception {
        UUID deviceId = UUID.randomUUID();
        Path source = Files.createDirectory(tempDir.resolve("source"));
        TrackingBackendClient backend = new TrackingBackendClient();

        try (FailingManifestDatabase db = new FailingManifestDatabase(tempDir.resolve("agent.sqlite"));
             MockedConstruction<DirectTransferStorage> ignored = mockTransferStorage(backend.transferSessionId)) {
            BackupEngine backupEngine = new BackupEngine(backend, db);

            BackupSnapshotException error = assertThrows(BackupSnapshotException.class, () ->
                    backupEngine.backup(deviceId, source));

            assertEquals(backend.snapshotId, error.snapshotId());
            assertEquals(backend.transferSessionId, error.transferSessionId());
            assertEquals(backend.transferSessionId, backend.cancelledTransferSessionId);
            assertEquals(backend.snapshotId, backend.failedSnapshotId);
            assertTrue(backend.failedSnapshotMessage.contains("Backup falhou antes da conclusao do snapshot"));
        }
    }

    @Test
    void backupValidatesUploadedSnapshotBeforeCompletionWhenEnabled() throws Exception {
        Path source = Files.createDirectory(tempDir.resolve("validated-source"));
        Files.writeString(source.resolve("data.txt"), "validated payload");
        TrackingBackendClient backend = new TrackingBackendClient(true);
        List<BackupProgressListener.BackupProgress> progressEvents = new ArrayList<>();

        try (LocalDatabase db = new LocalDatabase(tempDir.resolve("validated.sqlite").toString());
             MockedConstruction<DirectTransferStorage> ignored = mockTransferStorageWithRemoteObjects(
                     backend.transferSessionId, tempDir.resolve("uploaded-objects"), false)) {
            BackupEngine backupEngine = new BackupEngine(backend, db, progressEvents::add);
            backupEngine.backup(UUID.randomUUID(), source);
        }

        assertEquals(1, backend.completeSnapshotCalls);
        assertTrue(progressEvents.stream().anyMatch(event -> event.message().equals("Validando backup")));
        assertTrue(progressEvents.stream().anyMatch(event -> event.message().equals("Validação concluída")));
    }

    @Test
    void backupFailsWhenPostBackupValidationCannotReadRemoteChunk() throws Exception {
        Path source = Files.createDirectory(tempDir.resolve("invalid-source"));
        Files.writeString(source.resolve("data.txt"), "chunk missing remotely");
        TrackingBackendClient backend = new TrackingBackendClient(true);

        try (LocalDatabase db = new LocalDatabase(tempDir.resolve("invalid.sqlite").toString());
             MockedConstruction<DirectTransferStorage> ignored = mockTransferStorageWithRemoteObjects(
                     backend.transferSessionId, tempDir.resolve("uploaded-missing"), true)) {
            BackupEngine backupEngine = new BackupEngine(backend, db);

            BackupSnapshotException error = assertThrows(BackupSnapshotException.class,
                    () -> backupEngine.backup(UUID.randomUUID(), source));

            assertTrue(error.userMessage().contains("Validação pós-backup falhou"));
            assertEquals(0, backend.completeSnapshotCalls);
            assertEquals(backend.snapshotId, backend.failedSnapshotId);
        }
    }

    private static MockedConstruction<DirectTransferStorage> mockTransferStorage(UUID sessionId) {
        return mockConstruction(DirectTransferStorage.class, (mock, context) -> {
            when(mock.sessionId()).thenReturn(sessionId);
            doNothing().when(mock).uploadManifest(org.mockito.ArgumentMatchers.any());
            doNothing().when(mock).uploadChunk(org.mockito.ArgumentMatchers.anyString(),
                    org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        });
    }

    private MockedConstruction<DirectTransferStorage> mockTransferStorageWithRemoteObjects(UUID sessionId,
                                                                                           Path outputDir,
                                                                                           boolean omitChunkFromRemote) {
        return mockConstruction(DirectTransferStorage.class, (mock, context) -> {
            when(mock.sessionId()).thenReturn(sessionId);
            Map<String, Path> uploadedChunks = new HashMap<>();
            Path[] manifestHolder = new Path[1];

            doAnswer(invocation -> {
                Path source = invocation.getArgument(0);
                Files.createDirectories(outputDir);
                Path stored = outputDir.resolve("manifest.json.zst");
                Files.copy(source, stored, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                manifestHolder[0] = stored;
                return null;
            }).when(mock).uploadManifest(any());

            doAnswer(invocation -> {
                String hash = invocation.getArgument(0);
                Path source = invocation.getArgument(1);
                if (!omitChunkFromRemote) {
                    Files.createDirectories(outputDir);
                    Path stored = outputDir.resolve(hash + ".zst");
                    Files.copy(source, stored, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    uploadedChunks.put(hash, stored);
                }
                return null;
            }).when(mock).uploadChunk(anyString(), any(), any());

            doAnswer(invocation -> Files.newInputStream(manifestHolder[0])).when(mock).openManifest(any());
            doAnswer(invocation -> {
                Path stored = uploadedChunks.get(invocation.getArgument(0));
                if (stored == null) {
                    throw new IllegalStateException("chunk missing");
                }
                return Files.newInputStream(stored);
            }).when(mock).openChunk(anyString(), any());
            doAnswer(invocation -> new TransferObjectClient.StoredObjectInfo(true, Files.size(manifestHolder[0])))
                    .when(mock).statManifest(any());
            doAnswer(invocation -> {
                Path stored = uploadedChunks.get(invocation.getArgument(0));
                if (stored == null) {
                    throw new IllegalStateException("chunk missing");
                }
                return new TransferObjectClient.StoredObjectInfo(true, Files.size(stored));
            }).when(mock).statChunk(anyString(), any());
        });
    }

    private static List<Integer> processingPercents(List<BackupProgressListener.BackupProgress> events) {
        return events.stream()
                .filter(event -> event.message().startsWith("Processando arquivos"))
                .map(BackupProgressListener.BackupProgress::percent)
                .toList();
    }

    private static void assertMonotonic(List<Integer> percents) {
        for (int index = 1; index < percents.size(); index++) {
            assertTrue(percents.get(index) >= percents.get(index - 1),
                    "percent recuou em " + index + ": " + percents);
        }
    }

    private static final class TrackingBackendClient extends BackendClient {
        private final UUID snapshotId = UUID.randomUUID();
        private final UUID transferSessionId = UUID.randomUUID();
        private final boolean validationEnabled;
        private UUID cancelledTransferSessionId;
        private UUID failedSnapshotId;
        private String failedSnapshotMessage;
        private int startSnapshotCalls;
        private int completeSnapshotCalls;

        private TrackingBackendClient() {
            this(false);
        }

        private TrackingBackendClient(boolean validationEnabled) {
            super("http://localhost:8080", null);
            this.validationEnabled = validationEnabled;
        }

        @Override
        public List<SnapshotSummary> listSnapshots() {
            return Collections.emptyList();
        }

        @Override
        public StartedSnapshot startSnapshot(UUID deviceId, String path) {
            startSnapshotCalls++;
            return new StartedSnapshot(
                    new SnapshotSummary(snapshotId, deviceId, "IN_PROGRESS", path, 0L, 0L, 0L, null, null, null),
                    new TransferCredentials(transferSessionId, "S3", "bucket", "http://localhost:9000", "key", "secret",
                            "token", Instant.now().plusSeconds(300), null, "prefix/")
            );
        }

        @Override
        public BackendClient.CheckChunksResult checkChunks(List<String> hashes) {
            return new BackendClient.CheckChunksResult(Collections.emptyList(), List.of());
        }

        @Override
        public java.util.Optional<com.keeply.agent.model.ProtectionPlan> getDevicePlan(UUID deviceId) {
            return java.util.Optional.of(new com.keeply.agent.model.ProtectionPlan(
                    com.keeply.agent.model.ProtectionPlan.PlanType.CUSTOM,
                    List.of("/validated-source"),
                    false,
                    validationEnabled,
                    false,
                    "0 2 * * *",
                    com.keeply.agent.model.ProtectionPlan.RetentionMode.KEEP_ALL,
                    null,
                    Instant.now()));
        }

        @Override
        public void completeSnapshot(UUID snapshotId, UUID transferSessionId, long totalFiles,
                                     long totalOriginalSize, long totalCompressedSize) {
            completeSnapshotCalls++;
        }

        @Override
        public SnapshotSummary getSnapshot(UUID snapshotId) {
            return new SnapshotSummary(snapshotId, UUID.randomUUID(), "COMPLETED", "/source", 0L, 0L, 0L,
                    null, null, null);
        }

        @Override
        public void cancelTransferSession(UUID transferSessionId) {
            this.cancelledTransferSessionId = transferSessionId;
        }

        @Override
        public void failSnapshot(UUID snapshotId, String errorMessage) {
            this.failedSnapshotId = snapshotId;
            this.failedSnapshotMessage = errorMessage;
        }
    }

    private static class CachedOnlyDatabase extends LocalDatabase {
        private CachedOnlyDatabase(Path path) {
            super(path.toString());
        }

        @Override
        public synchronized List<ChunkMetadata> cachedChunksIfUnchanged(String sourcePath, String relativePath,
                                                                        long size, long lastModified) {
            return List.of();
        }

        @Override
        public synchronized int copyCachedFileToManifestIfValid(String sourcePath, String relativePath,
                                                                long size, long lastModified) {
            addManifestFile(relativePath, size, lastModified, "cached-" + relativePath);
            return 1;
        }

        @Override
        public synchronized void writeManifestZstd(Path output, String snapshotId, String sourcePath) {
            try {
                Files.writeString(output, "{}");
            } catch (Exception e) {
                throw new IllegalStateException("manifest write failed", e);
            }
        }
    }

    private static final class FailingManifestDatabase extends CachedOnlyDatabase {
        private FailingManifestDatabase(Path path) {
            super(path);
        }

        @Override
        public synchronized void writeManifestZstd(Path output, String snapshotId, String sourcePath) {
            throw new IllegalStateException("manifest write failed");
        }
    }
}
