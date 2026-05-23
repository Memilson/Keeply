package com.keeply.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.keeply.backend.dto.ManifestParsingDtos;
import com.keeply.backend.dto.SnapshotDtos;
import com.keeply.backend.model.Snapshot;
import com.keeply.backend.model.SnapshotStatus;
import com.keeply.backend.repository.SnapshotRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ManifestReaderServiceAuditTest {

    @Mock private ObjectStorageService storage;
    @Mock private SnapshotRepository snapshots;
    
    private ManifestReaderService service;
    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

    private UUID userId;
    private UUID snapshotId;
    private Snapshot snapshot;
    private byte[] compressedManifest;

    @BeforeEach
    void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);
        service = new ManifestReaderService(storage, snapshots, mapper);

        userId = UUID.randomUUID();
        snapshotId = UUID.randomUUID();
        
        snapshot = new Snapshot();
        snapshot.id = snapshotId;
        snapshot.userId = userId;
        snapshot.status = SnapshotStatus.COMPLETED;
        snapshot.manifestKey = "manifest.json.gz";

        // Criar um manifesto com 5 arquivos para testes controlados
        List<ManifestParsingDtos.FileManifest> files = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            files.add(new ManifestParsingDtos.FileManifest(
                    "file_" + i + ".txt",
                    100,
                    Instant.now(),
                    "hash" + i,
                    List.of()
            ));
        }
        ManifestParsingDtos.SnapshotManifest manifest = new ManifestParsingDtos.SnapshotManifest(
                snapshotId.toString(), "/source", Instant.now(), "CDC", "GZIP", "SHA-256", files
        );

        byte[] json = mapper.writeValueAsBytes(manifest);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (GZIPOutputStream gos = new GZIPOutputStream(bos)) {
            gos.write(json);
        }
        compressedManifest = bos.toByteArray();
    }

    @Test
    void auditSecurity_UserIdMustBeChecked() {
        UUID wrongUserId = UUID.randomUUID();
        when(snapshots.findByIdAndUserId(snapshotId, wrongUserId)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> {
            service.listFiles(wrongUserId, snapshotId, 0, 10, null);
        });
        
        verify(snapshots).findByIdAndUserId(snapshotId, wrongUserId);
    }

    @Test
    void auditCache_MissThenHit() {
        when(snapshots.findByIdAndUserId(snapshotId, userId)).thenReturn(Optional.of(snapshot));
        when(storage.get(snapshot.manifestKey)).thenReturn(compressedManifest);

        // First call: Cache Miss
        service.listFiles(userId, snapshotId, 0, 10, null);
        verify(storage, times(1)).get(anyString());

        // Second call: Cache Hit
        service.listFiles(userId, snapshotId, 0, 10, null);
        verify(storage, times(1)).get(anyString()); // No additional call to storage
    }

    @Test
    void auditFiltering_SearchString() {
        when(snapshots.findByIdAndUserId(snapshotId, userId)).thenReturn(Optional.of(snapshot));
        when(storage.get(snapshot.manifestKey)).thenReturn(compressedManifest);

        SnapshotDtos.SnapshotFileListResponse response = service.listFiles(userId, snapshotId, 0, 10, "file_3");
        
        assertEquals(1, response.items().size());
        assertEquals("file_3.txt", response.items().get(0).path());
    }

    @Test
    void auditPagination_EdgeCases() {
        when(snapshots.findByIdAndUserId(snapshotId, userId)).thenReturn(Optional.of(snapshot));
        when(storage.get(snapshot.manifestKey)).thenReturn(compressedManifest);

        // Page 0, Size 2 (Should return files 0, 1)
        SnapshotDtos.SnapshotFileListResponse page0 = service.listFiles(userId, snapshotId, 0, 2, null);
        assertEquals(2, page0.items().size());
        assertEquals(5, page0.pagination().totalElements());

        // Page 2, Size 2 (Should return file 4)
        SnapshotDtos.SnapshotFileListResponse page2 = service.listFiles(userId, snapshotId, 2, 2, null);
        assertEquals(1, page2.items().size());
        assertEquals("file_4.txt", page2.items().get(0).path());

        // Page 3, Size 2 (Out of bounds, should return empty)
        SnapshotDtos.SnapshotFileListResponse page3 = service.listFiles(userId, snapshotId, 3, 2, null);
        assertEquals(0, page3.items().size());
    }
}
