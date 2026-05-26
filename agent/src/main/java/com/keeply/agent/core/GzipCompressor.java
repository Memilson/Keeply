package com.keeply.agent.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public final class GzipCompressor {
    private static final Logger log = LoggerFactory.getLogger(GzipCompressor.class);
    private GzipCompressor() {}

    public static byte[] compress(byte[] input) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (GZIPOutputStream gzip = new GZIPOutputStream(out)) {
                gzip.write(input);
            }
            byte[] compressed = out.toByteArray();
            log.debug("🗜️ Compressão GZIP: {} -> {} bytes ({.2f}%)", 
                    input.length, compressed.length, (compressed.length * 100.0) / input.length);
            return compressed;
        } catch (Exception e) {
            log.error("❌ Falha ao comprimir GZIP: {}", e.getMessage());
            throw new IllegalStateException("Falha ao comprimir GZIP", e);
        }
    }

    public static byte[] decompress(byte[] input) {
        try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(input))) {
            byte[] decompressed = gzip.readAllBytes();
            log.debug("🗜️ Descompressão GZIP: {} -> {} bytes", input.length, decompressed.length);
            return decompressed;
        } catch (Exception e) {
            log.error("❌ Falha ao descomprimir GZIP: {}", e.getMessage());
            throw new IllegalStateException("Falha ao descomprimir GZIP", e);
        }
    }
}
