package com.keeply.agent.core;

import com.github.luben.zstd.ZstdInputStream;
import com.github.luben.zstd.ZstdOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Path;

public final class ZstdCompressor {
    public static final int COMPRESSION_LEVEL = ZstdChunkCodec.DEFAULT_LEVEL;

    private ZstdCompressor() {
    }

    public static byte[] compress(byte[] input) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (ZstdOutputStream zstd = new ZstdOutputStream(out, COMPRESSION_LEVEL)) {
                zstd.write(input);
            }
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao comprimir ZSTD", e);
        }
    }

    public static long compressToFile(byte[] input, Path output) {
        return new ZstdChunkCodec().compressToFile(input, output);
    }

    public static byte[] decompress(byte[] input) {
        try (ZstdInputStream zstd = new ZstdInputStream(new ByteArrayInputStream(input))) {
            return zstd.readAllBytes();
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao descomprimir ZSTD", e);
        }
    }
}
