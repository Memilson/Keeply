package com.keeply.agent.core;

import java.io.InputStream;
import java.nio.file.Path;

public interface ChunkCodec {
    String algorithm();

    Integer level();

    String extension();

    String contentType();

    long compressToFile(byte[] input, Path output);

    InputStream openDecompressing(InputStream input);
}
