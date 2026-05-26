package com.keeply.backend.service;

import com.keeply.backend.model.Snapshot;
import com.keeply.backend.model.SnapshotStatus;
import com.keeply.backend.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Optional;
import java.util.UUID;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SnapshotServiceTest {

    @Mock private SnapshotRepository snapshots;
    @Mock private DeviceRepository devices;
    @Mock private ObjectStorageService storage;
    @Mock private ManifestParserService manifestParser;

    private SnapshotService snapshotService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        snapshotService = new SnapshotService(snapshots, devices, storage, manifestParser);
    }

    @Test
    void testManifestShouldBlockInProgressSnapshot() {
        UUID userId = UUID.randomUUID();
        UUID snapshotId = UUID.randomUUID();
        Snapshot s = new Snapshot();
        s.id = snapshotId;
        s.status = SnapshotStatus.IN_PROGRESS;

        when(snapshots.findByIdAndDeviceUserId(snapshotId, userId)).thenReturn(Optional.of(s));

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> {
            snapshotService.manifest(userId, snapshotId);
        });
        assertEquals("Somente snapshots concluídos ou em processamento podem ter o manifesto lido", ex.getMessage());
    }

    @Test
    void testManifestShouldBlockFailedSnapshot() {
        UUID userId = UUID.randomUUID();
        UUID snapshotId = UUID.randomUUID();
        Snapshot s = new Snapshot();
        s.id = snapshotId;
        s.status = SnapshotStatus.FAILED;

        when(snapshots.findByIdAndDeviceUserId(snapshotId, userId)).thenReturn(Optional.of(s));

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> {
            snapshotService.manifest(userId, snapshotId);
        });
        assertEquals("Somente snapshots concluídos ou em processamento podem ter o manifesto lido", ex.getMessage());
    }

    @Test
    void testManifestShouldAllowCompletedSnapshot() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID snapshotId = UUID.randomUUID();
        Snapshot s = new Snapshot();
        s.id = snapshotId;
        s.status = SnapshotStatus.COMPLETED;
        s.manifestKey = "manifest.json.gz";

        String json = "{\"test\":true}";
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (GZIPOutputStream gos = new GZIPOutputStream(bos)) {
            gos.write(json.getBytes());
        }

        when(snapshots.findByIdAndDeviceUserId(snapshotId, userId)).thenReturn(Optional.of(s));
        when(storage.getStream("manifest.json.gz")).thenReturn(new ByteArrayInputStream(bos.toByteArray()));

        try (var manifest = snapshotService.manifest(userId, snapshotId)) {
            assertEquals(json, new String(manifest.readAllBytes()));
        }
    }
}
