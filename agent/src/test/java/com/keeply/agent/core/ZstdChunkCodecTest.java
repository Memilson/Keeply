package com.keeply.agent.core;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ZstdChunkCodecTest {
    @TempDir
    Path tempDir;

    @Test
    void roundTripPreservesRawBytesAndUsesCanonicalMetadata() throws Exception {
        byte[] raw = "keeply chunk payload".repeat(100).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Path compressed = tempDir.resolve("chunk.zst");
        ZstdChunkCodec codec = new ZstdChunkCodec();

        long storedSize = codec.compressToFile(raw, compressed);

        assertEquals("ZSTD", codec.algorithm());
        assertEquals(3, codec.level());
        assertEquals(Files.size(compressed), storedSize);
        try (InputStream input = codec.openDecompressing(Files.newInputStream(compressed))) {
            assertArrayEquals(raw, input.readAllBytes());
        }
    }

    @Test
    void corruptedChunkFailsDuringDecompression() throws Exception {
        Path compressed = tempDir.resolve("corrupt.zst");
        Files.write(compressed, new byte[] {1, 2, 3, 4, 5});
        ZstdChunkCodec codec = new ZstdChunkCodec();

        assertThrows(Exception.class, () -> {
            try (InputStream input = codec.openDecompressing(Files.newInputStream(compressed))) {
                input.readAllBytes();
            }
        });
    }
}
