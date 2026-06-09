package com.keeply.backend.service;

import com.github.luben.zstd.Zstd;
import com.keeply.backend.model.FileChunk;
import com.keeply.backend.model.Snapshot;
import com.keeply.backend.model.SnapshotFile;
import com.keeply.backend.model.SnapshotStatus;
import com.keeply.backend.repository.FileChunkRepository;
import com.keeply.backend.repository.SnapshotFileRepository;
import com.keeply.backend.repository.SnapshotRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

class FileDownloadServiceTest {

    @Mock
    private SnapshotRepository snapshotRepository;
    @Mock
    private SnapshotFileRepository snapshotFileRepository;
    @Mock
    private FileChunkRepository fileChunkRepository;
    @Mock
    private ObjectStorageService objectStorage;

    private FileDownloadService fileDownloadService;
    private UUID userId;
    private UUID snapshotId;
    private Snapshot snapshot;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        fileDownloadService = new FileDownloadService(snapshotRepository, snapshotFileRepository, fileChunkRepository, objectStorage);
        userId = UUID.randomUUID();
        snapshotId = UUID.randomUUID();
        snapshot = new Snapshot();
        snapshot.id = snapshotId;
        snapshot.status = SnapshotStatus.COMPLETED;

        when(snapshotRepository.findByIdAndDeviceUserId(snapshotId, userId)).thenReturn(Optional.of(snapshot));
    }

    @Test
    void streamSelectedArchive_shouldRejectEmptySelection() {
        MockHttpServletResponse response = new MockHttpServletResponse();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> fileDownloadService.streamSelectedArchive(userId, snapshotId, List.of(), response));

        assertEquals("At least one file path must be provided", ex.getMessage());
    }

    @Test
    void streamSelectedArchive_shouldRejectMissingFile() {
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(snapshotFileRepository.findBySnapshotIdAndPath(snapshotId, "missing.txt")).thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> fileDownloadService.streamSelectedArchive(userId, snapshotId, List.of("missing.txt"), response));

        assertEquals("File not found in snapshot: missing.txt", ex.getMessage());
    }

    @Test
    void streamSelectedArchive_shouldRejectIncompleteSnapshot() {
        snapshot.status = SnapshotStatus.PROCESSING;
        MockHttpServletResponse response = new MockHttpServletResponse();

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> fileDownloadService.streamSelectedArchive(userId, snapshotId, List.of("docs/a.txt"), response));

        assertEquals("Snapshot is not completed: " + snapshotId, ex.getMessage());
    }

    @Test
    void streamSelectedArchive_shouldReturnZipWithSelectedFiles() throws Exception {
        Map<String, String> contents = new LinkedHashMap<>();
        contents.put("docs/a.txt", "alpha");
        contents.put("docs/b.txt", "bravo");
        stubFiles(contents);

        MockHttpServletResponse response = new MockHttpServletResponse();
        fileDownloadService.streamSelectedArchive(userId, snapshotId, List.copyOf(contents.keySet()), response);

        assertEquals("application/zip", response.getContentType());
        assertTrue(response.getHeader("Content-Disposition").contains("keeply-selected-" + snapshotId + ".zip"));
        assertZipContents(response.getContentAsByteArray(), Map.of(
                "a.txt", "alpha",
                "b.txt", "bravo"
        ));
    }

    @Test
    void streamSelectedArchive_shouldSupportMoreThanTenFiles() throws Exception {
        Map<String, String> contents = new LinkedHashMap<>();
        for (int i = 1; i <= 12; i++) {
            contents.put("folder/file-" + i + ".txt", "content-" + i);
        }
        stubFiles(contents);

        MockHttpServletResponse response = new MockHttpServletResponse();
        fileDownloadService.streamSelectedArchive(userId, snapshotId, List.copyOf(contents.keySet()), response);

        Map<String, String> expected = new LinkedHashMap<>();
        for (int i = 1; i <= 12; i++) {
            expected.put("file-" + i + ".txt", "content-" + i);
        }
        assertZipContents(response.getContentAsByteArray(), expected);
    }

    @Test
    void streamSelectedArchive_shouldKeepDuplicateBasenames() throws Exception {
        Map<String, String> contents = new LinkedHashMap<>();
        contents.put("docs/report.txt", "first");
        contents.put("exports/report.txt", "second");
        stubFiles(contents);

        MockHttpServletResponse response = new MockHttpServletResponse();
        fileDownloadService.streamSelectedArchive(userId, snapshotId, List.copyOf(contents.keySet()), response);

        assertZipContents(response.getContentAsByteArray(), Map.of(
                "report.txt", "first",
                "report (2).txt", "second"
        ));
    }

    @Test
    void streamSelectedArchive_shouldExpandFolderPaths() throws Exception {
        Map<String, String> contents = new LinkedHashMap<>();
        contents.put("storage/projetos/Keeply/README.md", "readme");
        contents.put("storage/projetos/Keeply/frontend/package.json", "package");
        contents.put("storage/outside.txt", "outside");
        Map<String, SnapshotFile> files = stubFiles(contents);

        when(snapshotFileRepository.findBySnapshotIdAndPathStartingWith(
                snapshotId,
                "storage/projetos/Keeply/",
                org.springframework.data.domain.Sort.by("path").ascending()
        )).thenReturn(List.of(
                files.get("storage/projetos/Keeply/README.md"),
                files.get("storage/projetos/Keeply/frontend/package.json")
        ));

        MockHttpServletResponse response = new MockHttpServletResponse();
        fileDownloadService.streamSelectedArchive(userId, snapshotId, List.of("storage/projetos/Keeply/"), response);

        assertZipContents(response.getContentAsByteArray(), Map.of(
                "README.md", "readme",
                "frontend/package.json", "package"
        ));
    }

    @Test
    void streamSelectedArchive_shouldExpandEmptyPathAsWholeSnapshot() throws Exception {
        Map<String, String> contents = new LinkedHashMap<>();
        contents.put("storage/projetos/Keeply/README.md", "readme");
        contents.put("storage/outside.txt", "outside");
        Map<String, SnapshotFile> files = stubFiles(contents);

        when(snapshotFileRepository.findBySnapshotIdAndPathStartingWith(
                snapshotId,
                "",
                org.springframework.data.domain.Sort.by("path").ascending()
        )).thenReturn(List.of(
                files.get("storage/projetos/Keeply/README.md"),
                files.get("storage/outside.txt")
        ));

        MockHttpServletResponse response = new MockHttpServletResponse();
        fileDownloadService.streamSelectedArchive(userId, snapshotId, List.of(""), response);

        assertZipContents(response.getContentAsByteArray(), Map.of(
                "storage/projetos/Keeply/README.md", "readme",
                "storage/outside.txt", "outside"
        ));
    }

    private Map<String, SnapshotFile> stubFiles(Map<String, String> contents) {
        Map<String, SnapshotFile> files = new LinkedHashMap<>();
        contents.forEach((path, content) -> {
            SnapshotFile file = new SnapshotFile();
            file.id = UUID.randomUUID();
            file.snapshot = snapshot;
            file.path = path;
            file.size = content.getBytes(StandardCharsets.UTF_8).length;
            file.lastModified = Instant.parse("2026-06-02T12:00:00Z");

            FileChunk chunk = new FileChunk();
            chunk.snapshotFile = file;
            chunk.chunkHash = ("%064x".formatted(Math.abs(file.id.getMostSignificantBits()) + 1)).substring(0, 64);
            chunk.chunkIndex = 0;

            when(snapshotFileRepository.findBySnapshotIdAndPath(snapshotId, path)).thenReturn(Optional.of(file));
            when(fileChunkRepository.findBySnapshotFileIdOrderByChunkIndexAsc(file.id)).thenReturn(List.of(chunk));
            when(objectStorage.getStream(eq(ChunkService.chunkKey(userId, chunk.chunkHash))))
                    .thenReturn(new ByteArrayInputStream(Zstd.compress(content.getBytes(StandardCharsets.UTF_8))));
            files.put(path, file);
        });
        return files;
    }

    private void assertZipContents(byte[] zipBytes, Map<String, String> expected) throws IOException {
        Map<String, String> actual = new LinkedHashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                zip.transferTo(out);
                actual.put(entry.getName(), out.toString(StandardCharsets.UTF_8));
            }
        }

        assertEquals(expected, actual);
    }
}
