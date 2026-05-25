package com.keeply.backend.service;

import com.keeply.backend.dto.SnapshotDtos;
import com.keeply.backend.model.Snapshot;
import com.keeply.backend.model.SnapshotFile;
import com.keeply.backend.model.SnapshotStatus;
import com.keeply.backend.repository.SnapshotFileRepository;
import com.keeply.backend.repository.SnapshotRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

class ManifestReaderServiceStressTest {

    @Mock private SnapshotRepository snapshots;
    @Mock private SnapshotFileRepository snapshotFiles;

    private ManifestReaderService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new ManifestReaderService(snapshots, snapshotFiles);
    }

    @Test
    void testListFilesWith100kFiles() {
        UUID userId = UUID.randomUUID();
        UUID snapshotId = UUID.randomUUID();

        Snapshot snapshot = new Snapshot();
        snapshot.id = snapshotId;
        snapshot.status = SnapshotStatus.COMPLETED;

        List<SnapshotFile> files = new ArrayList<>();
        for (int i = 0; i < 100_000; i++) {
            SnapshotFile file = new SnapshotFile();
            file.path = "path/to/file_" + String.format("%06d", i) + ".txt";
            file.size = 1024;
            file.lastModified = Instant.now();
            files.add(file);
        }

        when(snapshots.findByIdAndDeviceUserId(snapshotId, userId)).thenReturn(Optional.of(snapshot));
        when(snapshotFiles.findBySnapshotId(snapshotId)).thenReturn(files);

        SnapshotDtos.SnapshotFileListResponse firstPage = service.listFiles(userId, snapshotId, 0, 100, null);
        assertEquals(100, firstPage.items().size());
        assertEquals(100_000, firstPage.pagination().totalElements());
        assertEquals("path/to/file_000000.txt", firstPage.items().get(0).path());

        SnapshotDtos.SnapshotFileListResponse secondPage = service.listFiles(userId, snapshotId, 1, 100, null);
        assertEquals(100, secondPage.items().size());
        assertEquals("path/to/file_000100.txt", secondPage.items().get(0).path());

        SnapshotDtos.SnapshotFileListResponse searchResult =
                service.listFiles(userId, snapshotId, 0, 100, "file_000999");
        assertEquals(1, searchResult.items().size());
        assertEquals("path/to/file_000999.txt", searchResult.items().get(0).path());
    }
}
