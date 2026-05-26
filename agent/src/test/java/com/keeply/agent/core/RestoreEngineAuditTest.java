package com.keeply.agent.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.keeply.agent.api.BackendClient;
import com.keeply.agent.model.FileManifest;
import com.keeply.agent.model.ManifestChunk;
import com.keeply.agent.model.SnapshotManifest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class RestoreEngineAuditTest {

    @TempDir
    Path tempDir;

    private StubBackendClient backend;
    private RestoreEngine restoreEngine;
    private ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    private static class StubBackendClient extends BackendClient {
        String manifestJson;
        Map<String, byte[]> chunks = new HashMap<>();

        public StubBackendClient() {
            super("http://localhost");
        }

        @Override
        public InputStream openManifestStream(UUID snapshotId) {
            return new ByteArrayInputStream(manifestJson.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }

        @Override
        public byte[] downloadChunk(String hash) {
            if (!chunks.containsKey(hash)) throw new RuntimeException("Chunk not found: " + hash);
            return chunks.get(hash);
        }
    }

    @BeforeEach
    void setUp() {
        backend = new StubBackendClient();
        restoreEngine = new RestoreEngine(backend);
    }

    @Test
    void testMultiChunkReconstructionAndIntegrity() throws IOException {
        Path dest = tempDir.resolve("restore_dest");
        UUID snapshotId = UUID.randomUUID();
        
        String chunk1Content = "Hello ";
        String chunk2Content = "World!";
        byte[] chunk1Bytes = chunk1Content.getBytes();
        byte[] chunk2Bytes = chunk2Content.getBytes();
        
        String hash1 = Sha256Hasher.hashBytes(chunk1Bytes);
        String hash2 = Sha256Hasher.hashBytes(chunk2Bytes);
        
        byte[] chunk1Gzip = GzipCompressor.compress(chunk1Bytes);
        byte[] chunk2Gzip = GzipCompressor.compress(chunk2Bytes);
        
        String fullContent = chunk1Content + chunk2Content;
        String fullHash = Sha256Hasher.hashBytes(fullContent.getBytes());
        
        ManifestChunk mc1 = new ManifestChunk(0, hash1, chunk1Bytes.length, chunk1Gzip.length);
        ManifestChunk mc2 = new ManifestChunk(1, hash2, chunk2Bytes.length, chunk2Gzip.length);
        
        FileManifest fm = new FileManifest("test.txt", fullContent.length(), Instant.now(), fullHash, List.of(mc1, mc2));
        SnapshotManifest sm = new SnapshotManifest(snapshotId.toString(), tempDir.toString(), Instant.now(), "CDP", "GZIP", "SHA-256", List.of(fm));
        
        backend.manifestJson = mapper.writeValueAsString(sm);
        backend.chunks.put(hash1, chunk1Gzip);
        backend.chunks.put(hash2, chunk2Gzip);
        
        restoreEngine.restore(snapshotId, dest);
        
        Path restoredFile = dest.resolve("test.txt");
        assertTrue(Files.exists(restoredFile));
        assertEquals(fullContent, Files.readString(restoredFile));
        assertEquals(fullHash, Sha256Hasher.hashFile(restoredFile));
    }

    @Test
    void testPathTraversalProtection() throws IOException {
        Path dest = tempDir.resolve("restore_dest");
        Files.createDirectories(dest);
        UUID snapshotId = UUID.randomUUID();
        
        // Malicious manifest trying to write outside dest
        FileManifest fm = new FileManifest("../traversal.txt", 4, Instant.now(), "hash", Collections.emptyList());
        SnapshotManifest sm = new SnapshotManifest(snapshotId.toString(), tempDir.toString(), Instant.now(), "CDP", "GZIP", "SHA-256", List.of(fm));
        
        backend.manifestJson = mapper.writeValueAsString(sm);
        
        assertThrows(IllegalStateException.class, () -> {
            restoreEngine.restore(snapshotId, dest);
        });
        
        assertFalse(Files.exists(dest.getParent().resolve("traversal.txt")));
    }

    @Test
    void testDeterminism() throws IOException {
        Path dest = tempDir.resolve("restore_dest");
        UUID snapshotId = UUID.randomUUID();
        
        Instant originalMtime = Instant.parse("2023-01-01T10:00:00Z");
        String content = "Deterministic content";
        byte[] bytes = content.getBytes();
        String hash = Sha256Hasher.hashBytes(bytes);
        byte[] gzip = GzipCompressor.compress(bytes);
        
        ManifestChunk mc = new ManifestChunk(0, hash, bytes.length, gzip.length);
        FileManifest fm = new FileManifest("det.txt", bytes.length, originalMtime, hash, List.of(mc));
        SnapshotManifest sm = new SnapshotManifest(snapshotId.toString(), tempDir.toString(), Instant.now(), "CDP", "GZIP", "SHA-256", List.of(fm));
        
        backend.manifestJson = mapper.writeValueAsString(sm);
        backend.chunks.put(hash, gzip);
        
        restoreEngine.restore(snapshotId, dest);
        
        Path restoredFile = dest.resolve("det.txt");
        assertEquals(originalMtime.toEpochMilli(), Files.getLastModifiedTime(restoredFile).toMillis());
        assertEquals(content, Files.readString(restoredFile));
    }

    @Test
    void testIntegrityFailure() throws IOException {
        Path dest = tempDir.resolve("restore_dest");
        UUID snapshotId = UUID.randomUUID();
        
        String content = "Integrity check";
        byte[] bytes = content.getBytes();
        String hash = Sha256Hasher.hashBytes(bytes);
        
        // Mock returning WRONG content for the chunk
        byte[] wrongGzip = GzipCompressor.compress("Wrong content".getBytes());
        
        ManifestChunk mc = new ManifestChunk(0, hash, bytes.length, 0);
        FileManifest fm = new FileManifest("fail.txt", bytes.length, Instant.now(), hash, List.of(mc));
        SnapshotManifest sm = new SnapshotManifest(snapshotId.toString(), tempDir.toString(), Instant.now(), "CDP", "GZIP", "SHA-256", List.of(fm));
        
        backend.manifestJson = mapper.writeValueAsString(sm);
        backend.chunks.put(hash, wrongGzip);
        
        assertThrows(IllegalStateException.class, () -> {
            restoreEngine.restore(snapshotId, dest);
        });
    }

    @Test
    void testEmptyFileRestore() throws IOException {
        Path dest = tempDir.resolve("restore_dest");
        UUID snapshotId = UUID.randomUUID();

        String emptyHash = Sha256Hasher.hashBytes(new byte[0]);
        FileManifest fm = new FileManifest("empty.txt", 0, Instant.now(), emptyHash, Collections.emptyList());
        SnapshotManifest sm = new SnapshotManifest(snapshotId.toString(), tempDir.toString(), Instant.now(), "CDP", "GZIP", "SHA-256", List.of(fm));

        backend.manifestJson = mapper.writeValueAsString(sm);

        restoreEngine.restore(snapshotId, dest);

        Path restoredFile = dest.resolve("empty.txt");
        assertTrue(Files.exists(restoredFile));
        assertEquals(0, Files.size(restoredFile));
        assertEquals(emptyHash, Sha256Hasher.hashFile(restoredFile));
    }

    @Test
    void testAbsolutePathsRejection() throws IOException {
        Path dest = tempDir.resolve("restore_dest");
        UUID snapshotId = UUID.randomUUID();

        // Unix-style absolute path
        FileManifest fm1 = new FileManifest("/etc/passwd", 10, Instant.now(), "somehash", Collections.emptyList());
        // Windows-style absolute path (even on Linux, Path.of might not treat it as absolute but safeResolve should catch it)
        FileManifest fm2 = new FileManifest("C:\\Windows\\System32\\config", 10, Instant.now(), "otherhash", Collections.emptyList());

        backend.manifestJson = mapper.writeValueAsString(new SnapshotManifest(snapshotId.toString(), tempDir.toString(), Instant.now(), "CDP", "GZIP", "SHA-256", List.of(fm1)));
        assertThrows(IllegalStateException.class, () -> restoreEngine.restore(snapshotId, dest));

        backend.manifestJson = mapper.writeValueAsString(new SnapshotManifest(snapshotId.toString(), tempDir.toString(), Instant.now(), "CDP", "GZIP", "SHA-256", List.of(fm2)));
        assertThrows(IllegalStateException.class, () -> restoreEngine.restore(snapshotId, dest));
    }

    @Test
    void testMissingChunkFailure() throws IOException {
        Path dest = tempDir.resolve("restore_dest");
        UUID snapshotId = UUID.randomUUID();

        String hash = "nonexistent-hash";
        ManifestChunk mc = new ManifestChunk(0, hash, 10, 10);
        FileManifest fm = new FileManifest("missing.txt", 10, Instant.now(), "filehash", List.of(mc));
        SnapshotManifest sm = new SnapshotManifest(snapshotId.toString(), tempDir.resolve("orig").toString(), Instant.now(), "CDP", "GZIP", "SHA-256", List.of(fm));

        backend.manifestJson = mapper.writeValueAsString(sm);
        // Do NOT put chunk in backend

        assertThrows(IllegalStateException.class, () -> {
            restoreEngine.restore(snapshotId, dest);
        });
    }

    @Test
    void testCorruptedGzipFailure() throws IOException {
        Path dest = tempDir.resolve("restore_dest");
        UUID snapshotId = UUID.randomUUID();

        String content = "Something";
        String hash = Sha256Hasher.hashBytes(content.getBytes());
        byte[] corruptedGzip = "not a gzip".getBytes();

        ManifestChunk mc = new ManifestChunk(0, hash, content.length(), corruptedGzip.length);
        FileManifest fm = new FileManifest("corrupt.txt", content.length(), Instant.now(), hash, List.of(mc));
        SnapshotManifest sm = new SnapshotManifest(snapshotId.toString(), tempDir.resolve("orig").toString(), Instant.now(), "CDP", "GZIP", "SHA-256", List.of(fm));

        backend.manifestJson = mapper.writeValueAsString(sm);
        backend.chunks.put(hash, corruptedGzip);

        assertThrows(IllegalStateException.class, () -> {
            restoreEngine.restore(snapshotId, dest);
        });
    }

    @Test
    void testMultiFileRestore() throws IOException {
        Path dest = tempDir.resolve("restore_dest");
        UUID snapshotId = UUID.randomUUID();

        String c1 = "File 1";
        String h1 = Sha256Hasher.hashBytes(c1.getBytes());
        byte[] g1 = GzipCompressor.compress(c1.getBytes());

        String c2 = "File 2 content";
        String h2 = Sha256Hasher.hashBytes(c2.getBytes());
        byte[] g2 = GzipCompressor.compress(c2.getBytes());

        FileManifest fm1 = new FileManifest("f1.txt", c1.length(), Instant.now(), h1, List.of(new ManifestChunk(0, h1, c1.length(), g1.length)));
        FileManifest fm2 = new FileManifest("dir/f2.txt", c2.length(), Instant.now(), h2, List.of(new ManifestChunk(0, h2, c2.length(), g2.length)));

        SnapshotManifest sm = new SnapshotManifest(snapshotId.toString(), tempDir.resolve("orig").toString(), Instant.now(), "CDP", "GZIP", "SHA-256", List.of(fm1, fm2));

        backend.manifestJson = mapper.writeValueAsString(sm);
        backend.chunks.put(h1, g1);
        backend.chunks.put(h2, g2);

        restoreEngine.restore(snapshotId, dest);

        assertTrue(Files.exists(dest.resolve("f1.txt")));
        assertTrue(Files.exists(dest.resolve("dir/f2.txt")));
        assertEquals(c1, Files.readString(dest.resolve("f1.txt")));
        assertEquals(c2, Files.readString(dest.resolve("dir/f2.txt")));
    }
}
