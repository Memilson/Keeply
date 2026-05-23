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

class ManifestReaderServiceStressTest {

    @Mock private ObjectStorageService storage;
    @Mock private SnapshotRepository snapshots;
    
    private ManifestReaderService service;
    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new ManifestReaderService(storage, snapshots, mapper);
    }

    @Test
    void testListFilesWith100kFiles() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID snapshotId = UUID.randomUUID();
        
        Snapshot s = new Snapshot();
        s.id = snapshotId;
        s.userId = userId;
        s.status = SnapshotStatus.COMPLETED;
        s.manifestKey = "users/" + userId + "/manifests/" + snapshotId + ".json.gz";

        // Gerar manifesto fake com 100 mil arquivos
        List<ManifestParsingDtos.FileManifest> files = new ArrayList<>();
        for (int i = 0; i < 100_000; i++) {
            files.add(new ManifestParsingDtos.FileManifest(
                    "path/to/file_" + String.format("%06d", i) + ".txt",
                    1024,
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
        byte[] compressed = bos.toByteArray();

        when(snapshots.findByIdAndUserId(snapshotId, userId)).thenReturn(Optional.of(s));
        when(storage.get(s.manifestKey)).thenReturn(compressed);

        // Medir tempo da primeira carga (cache miss)
        long start = System.currentTimeMillis();
        SnapshotDtos.SnapshotFileListResponse firstPage = service.listFiles(userId, snapshotId, 0, 100, null);
        long duration = System.currentTimeMillis() - start;

        System.out.println("Carga inicial (100k arquivos + GZIP): " + duration + "ms");
        assertEquals(100, firstPage.items().size());
        assertEquals(100_000, firstPage.pagination().totalElements());
        assertEquals("path/to/file_000000.txt", firstPage.items().get(0).path());

        // Medir tempo da segunda carga (cache hit)
        start = System.currentTimeMillis();
        SnapshotDtos.SnapshotFileListResponse secondPage = service.listFiles(userId, snapshotId, 1, 100, null);
        duration = System.currentTimeMillis() - start;

        System.out.println("Carga em cache (hit): " + duration + "ms");
        assertEquals(100, secondPage.items().size());
        assertEquals("path/to/file_000100.txt", secondPage.items().get(0).path());
        assertTrue(duration < 50, "Cache hit deve ser ultra rápido (< 50ms)");

        // Testar busca
        start = System.currentTimeMillis();
        SnapshotDtos.SnapshotFileListResponse searchResult = service.listFiles(userId, snapshotId, 0, 100, "file_000999");
        duration = System.currentTimeMillis() - start;

        System.out.println("Busca em 100k arquivos: " + duration + "ms");
        assertEquals(1, searchResult.items().size());
        assertEquals("path/to/file_000999.txt", searchResult.items().get(0).path());
    }
}
