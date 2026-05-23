package com.keeply.agent.model;

import java.time.Instant;
import java.util.List;

public record FileManifest(
        String path,
        long size,
        Instant lastModified,
        String sha256,
        List<ManifestChunk> chunks
) {}
