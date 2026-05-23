package com.keeply.agent.core;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public final class GzipCompressor {
    private GzipCompressor() {}

    public static byte[] compress(byte[] input) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (GZIPOutputStream gzip = new GZIPOutputStream(out)) {
                gzip.write(input);
            }
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao comprimir GZIP", e);
        }
    }

    public static byte[] decompress(byte[] input) {
        try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(input))) {
            return gzip.readAllBytes();
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao descomprimir GZIP", e);
        }
    }
}
