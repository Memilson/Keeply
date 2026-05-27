package com.keeply.agent.core;

import com.github.luben.zstd.ZstdInputStream;
import com.github.luben.zstd.ZstdOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ZstdCompressor {
    public static final int COMPRESSION_LEVEL = 3;

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
        try (var out = Files.newOutputStream(output);
             ZstdOutputStream zstd = new ZstdOutputStream(out, COMPRESSION_LEVEL)) {
            zstd.write(input);
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao comprimir ZSTD para arquivo", e);
        }
        try {
            return Files.size(output);
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao obter tamanho do chunk ZSTD", e);
        }
    }

    public static byte[] decompress(byte[] input) {
        try (ZstdInputStream zstd = new ZstdInputStream(new ByteArrayInputStream(input))) {
            return zstd.readAllBytes();
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao descomprimir ZSTD", e);
        }
    }
}
