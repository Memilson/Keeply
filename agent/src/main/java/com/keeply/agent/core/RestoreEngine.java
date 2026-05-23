package com.keeply.agent.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.keeply.agent.api.BackendClient;
import com.keeply.agent.model.FileManifest;
import com.keeply.agent.model.ManifestChunk;
import com.keeply.agent.model.SnapshotManifest;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.function.Consumer;

public class RestoreEngine {
    private final BackendClient backend;
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    public RestoreEngine(BackendClient backend) {
        this.backend = backend;
    }

    public void restore(UUID snapshotId, Path destinationRoot, Consumer<String> log) {
        try {
            Files.createDirectories(destinationRoot);
            String json = backend.downloadManifest(snapshotId);
            SnapshotManifest manifest = mapper.readValue(json, SnapshotManifest.class);

            for (FileManifest file : manifest.files()) {
                Path target = safeResolve(destinationRoot, file.path());
                Files.createDirectories(target.getParent());

                try (OutputStream out = Files.newOutputStream(target)) {
                    for (ManifestChunk chunk : file.chunks()) {
                        byte[] gzip = backend.downloadChunk(chunk.hash());
                        byte[] original = GzipCompressor.decompress(gzip);

                        String hash = Sha256Hasher.hashBytes(original);
                        if (!hash.equals(chunk.hash())) {
                            throw new IllegalStateException("Hash inválido no chunk " + chunk.hash());
                        }

                        out.write(original);
                    }
                }

                String finalHash = Sha256Hasher.hashFile(target);
                if (!finalHash.equals(file.sha256())) {
                    throw new IllegalStateException("Hash final inválido no arquivo " + file.path());
                }

                log.accept("Restaurado: " + file.path());
            }

            log.accept("Restore concluído com integridade validada.");
        } catch (Exception e) {
            throw new IllegalStateException("Restore falhou", e);
        }
    }

    private Path safeResolve(Path root, String relativePath) {
        try {
            if (relativePath.startsWith("/") || relativePath.contains("..")) {
                throw new IllegalArgumentException("Caminho inválido no manifesto: " + relativePath);
            }

            Path normalizedRoot = root.toAbsolutePath().normalize();
            Path target = normalizedRoot.resolve(relativePath).normalize();

            if (!target.startsWith(normalizedRoot)) {
                throw new IllegalArgumentException("Path traversal bloqueado: " + relativePath);
            }

            return target;
        } catch (Exception e) {
            throw new IllegalArgumentException("Caminho inseguro no restore: " + relativePath, e);
        }
    }
}
