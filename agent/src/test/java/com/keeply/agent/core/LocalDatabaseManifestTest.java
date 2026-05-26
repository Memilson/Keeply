package com.keeply.agent.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.keeply.agent.model.SnapshotManifest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPInputStream;

import static org.junit.jupiter.api.Assertions.*;

class LocalDatabaseManifestTest {
    @TempDir
    Path tempDir;

    @Test
    void writesGzipManifestFromSqliteAndCountsDistinctChunks() throws Exception {
        Path manifest = tempDir.resolve("manifest.json.gz");
        try (LocalDatabase db = new LocalDatabase(tempDir.resolve("local.db").toString())) {
            db.clearBackupManifest();
            assertTrue(db.claimChunkForSession("a".repeat(64)));
            assertFalse(db.claimChunkForSession("a".repeat(64)));

            db.addManifestFile("a.txt", 3, 1_700_000_000_000L, "f".repeat(64));
            db.addManifestChunk("a.txt", 0, "a".repeat(64), 3, 10);
            db.addManifestFile("b.txt", 3, 1_700_000_000_000L, "e".repeat(64));
            db.addManifestChunk("b.txt", 0, "a".repeat(64), 3, 10);

            assertEquals(10, db.totalDistinctCompressedSize());
            db.writeManifestGzip(manifest, "snapshot", "/source");
        }

        try (GZIPInputStream gzip = new GZIPInputStream(Files.newInputStream(manifest))) {
            SnapshotManifest parsed = new ObjectMapper().findAndRegisterModules()
                    .readValue(gzip, SnapshotManifest.class);
            assertEquals(2, parsed.files().size());
            assertEquals("a.txt", parsed.files().get(0).path());
            assertEquals("a".repeat(64), parsed.files().get(1).chunks().get(0).hash());
        }
    }
}
