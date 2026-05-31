package com.keeply.agent.core;

public final class CompressionService {
    private final ChunkCodec writeCodec = new ZstdChunkCodec();

    public ChunkCodec writeCodec() {
        return writeCodec;
    }

    public ChunkCodec chunkCodec(String algorithm) {
        if (!writeCodec.algorithm().equalsIgnoreCase(algorithm)) {
            throw new IllegalArgumentException("Codec de chunk não suportado: " + algorithm);
        }
        return writeCodec;
    }
}
