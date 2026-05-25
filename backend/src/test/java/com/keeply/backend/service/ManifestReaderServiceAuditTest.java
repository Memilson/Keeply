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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ManifestReaderServiceAuditTest {

    @Mock private SnapshotRepository snapshots;
    @Mock private SnapshotFileRepository snapshotFiles;

    private ManifestReaderService service;
    private UUID userId;
    private UUID snapshotId;
    private Snapshot snapshot;
    private List<SnapshotFile> indexedFiles;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new ManifestReaderService(snapshots, snapshotFiles);

        userId = UUID.randomUUID();
        snapshotId = UUID.randomUUID();

        snapshot = new Snapshot();
        snapshot.id = snapshotId;
        snapshot.status = SnapshotStatus.COMPLETED;

        indexedFiles = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            SnapshotFile file = new SnapshotFile();
            file.path = "file_" + i + ".txt";
            file.size = 100;
            file.lastModified = Instant.now();
            indexedFiles.add(file);
        }
    }

    @Test
    void auditSecurityUserIdMustBeChecked() {
        UUID wrongUserId = UUID.randomUUID();
        when(snapshots.findByIdAndDeviceUserId(snapshotId, wrongUserId)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () ->
                service.listFiles(wrongUserId, snapshotId, 0, 10, null));

        verify(snapshots).findByIdAndDeviceUserId(snapshotId, wrongUserId);
    }

    @Test
    void auditReadsIndexedFilesForEachRequest() {
        stubIndexedFiles();

        service.listFiles(userId, snapshotId, 0, 10, null);
        service.listFiles(userId, snapshotId, 0, 10, null);

        verify(snapshotFiles, times(2)).findBySnapshotId(snapshotId);
    }

    @Test
    void auditFilteringSearchString() {
        stubIndexedFiles();

        SnapshotDtos.SnapshotFileListResponse response = service.listFiles(userId, snapshotId, 0, 10, "file_3");

        assertEquals(1, response.items().size());
        assertEquals("file_3.txt", response.items().get(0).path());
    }

    @Test
    void auditPaginationEdgeCases() {
        stubIndexedFiles();

        SnapshotDtos.SnapshotFileListResponse page0 = service.listFiles(userId, snapshotId, 0, 2, null);
        assertEquals(2, page0.items().size());
        assertEquals(5, page0.pagination().totalElements());

        SnapshotDtos.SnapshotFileListResponse page2 = service.listFiles(userId, snapshotId, 2, 2, null);
        assertEquals(1, page2.items().size());
        assertEquals("file_4.txt", page2.items().get(0).path());

        SnapshotDtos.SnapshotFileListResponse page3 = service.listFiles(userId, snapshotId, 3, 2, null);
        assertEquals(0, page3.items().size());
    }

    private void stubIndexedFiles() {
        when(snapshots.findByIdAndDeviceUserId(snapshotId, userId)).thenReturn(Optional.of(snapshot));
        when(snapshotFiles.findBySnapshotId(snapshotId)).thenReturn(indexedFiles);
    }
}
