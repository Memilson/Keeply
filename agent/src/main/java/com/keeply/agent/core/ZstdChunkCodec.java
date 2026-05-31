package com.keeply.agent.core;

import com.github.luben.zstd.ZstdInputStream;
import com.github.luben.zstd.ZstdOutputStream;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ZstdChunkCodec implements ChunkCodec {
    public static final int DEFAULT_LEVEL = 3;

    @Override
    public String algorithm() {
        return "ZSTD";
    }

    @Override
    public Integer level() {
        return DEFAULT_LEVEL;
    }

    @Override
    public String extension() {
        return ".zst";
    }

    @Override
    public String contentType() {
        return "application/zstd";
    }

    @Override
    public long compressToFile(byte[] input, Path output) {
        try (var out = Files.newOutputStream(output);
             ZstdOutputStream zstd = new ZstdOutputStream(out, DEFAULT_LEVEL)) {
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

    @Override
    public InputStream openDecompressing(InputStream input) {
        try {
            return new ZstdInputStream(input);
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao abrir stream ZSTD", e);
        }
    }
}
