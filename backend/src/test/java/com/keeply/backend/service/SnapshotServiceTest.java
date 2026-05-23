package com.keeply.backend.service;

import com.keeply.backend.model.Snapshot;
import com.keeply.backend.model.SnapshotStatus;
import com.keeply.backend.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

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

    private SnapshotService snapshotService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        snapshotService = new SnapshotService(snapshots, devices, storage);
    }

    @Test
    void testManifestShouldBlockInProgressSnapshot() {
        UUID userId = UUID.randomUUID();
        UUID snapshotId = UUID.randomUUID();
        Snapshot s = new Snapshot();
        s.id = snapshotId;
        s.userId = userId;
        s.status = SnapshotStatus.IN_PROGRESS;

        when(snapshots.findByIdAndUserId(snapshotId, userId)).thenReturn(Optional.of(s));

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> {
            snapshotService.manifest(userId, snapshotId);
        });
        assertEquals("Somente snapshots COMPLETED podem ser restaurados", ex.getMessage());
    }

    @Test
    void testManifestShouldBlockFailedSnapshot() {
        UUID userId = UUID.randomUUID();
        UUID snapshotId = UUID.randomUUID();
        Snapshot s = new Snapshot();
        s.id = snapshotId;
        s.userId = userId;
        s.status = SnapshotStatus.FAILED;

        when(snapshots.findByIdAndUserId(snapshotId, userId)).thenReturn(Optional.of(s));

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> {
            snapshotService.manifest(userId, snapshotId);
        });
        assertEquals("Somente snapshots COMPLETED podem ser restaurados", ex.getMessage());
    }

    @Test
    void testManifestShouldAllowCompletedSnapshot() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID snapshotId = UUID.randomUUID();
        Snapshot s = new Snapshot();
        s.id = snapshotId;
        s.userId = userId;
        s.status = SnapshotStatus.COMPLETED;
        s.manifestKey = "manifest.json.gz";

        String json = "{\"test\":true}";
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (GZIPOutputStream gos = new GZIPOutputStream(bos)) {
            gos.write(json.getBytes());
        }

        when(snapshots.findByIdAndUserId(snapshotId, userId)).thenReturn(Optional.of(s));
        when(storage.get("manifest.json.gz")).thenReturn(bos.toByteArray());

        String manifest = snapshotService.manifest(userId, snapshotId);
        assertEquals(json, manifest);
    }
}
