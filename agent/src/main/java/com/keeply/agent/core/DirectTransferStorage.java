package com.keeply.agent.core;

import com.keeply.agent.api.BackendClient;
import com.keeply.agent.model.TransferCredentials;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.credentials.StaticProvider;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;

public class DirectTransferStorage implements TransferObjectClient {
    private final BackendClient backend;
    private volatile TransferCredentials credentials;
    private volatile MinioClient minio;

    public DirectTransferStorage(BackendClient backend, TransferCredentials credentials) {
        this.backend = backend;
        update(credentials);
    }

    @Override
    public UUID sessionId() {
        return credentials.transferSessionId();
    }

    @Override
    public void uploadChunk(String hash, Path zstdFile) {
        String key = credentials.stagingPrefix() + "chunks/" + hash.substring(0, 2) + "/"
                + hash.substring(2, 4) + "/" + hash + ".zst";
        put(key, zstdFile);
    }

    @Override
    public void uploadManifest(Path zstdFile) {
        put(credentials.stagingPrefix() + "manifest.json.zst", zstdFile);
    }

    @Override
    public InputStream openManifest(UUID snapshotId) {
        return get("users/" + backend.getSession().userId() + "/manifests/" + snapshotId + ".json.zst");
    }

    @Override
    public InputStream openChunk(String hash) {
        return get("users/" + backend.getSession().userId() + "/chunks/" + hash.substring(0, 2) + "/"
                + hash.substring(2, 4) + "/" + hash + ".zst");
    }

    private void put(String key, Path source) {
        try {
            ensureRenewed();
            try (InputStream input = Files.newInputStream(source)) {
                minio.putObject(PutObjectArgs.builder()
                        .bucket(credentials.bucket())
                        .object(key)
                        .stream(input, Files.size(source), -1)
                        .contentType("application/zstd")
                        .build());
            }
        } catch (Exception e) {
            throw new IllegalStateException("Falha no upload direto MinIO: " + key, e);
        }
    }

    private InputStream get(String key) {
        try {
            ensureRenewed();
            return minio.getObject(GetObjectArgs.builder().bucket(credentials.bucket()).object(key).build());
        } catch (Exception e) {
            throw new IllegalStateException("Falha no download direto MinIO: " + key, e);
        }
    }
    private synchronized void ensureRenewed() {
        if (!Instant.now().isBefore(credentials.renewAfter())) {
            update(backend.renewTransferSession(credentials.transferSessionId()));
        }
    }
    private void update(TransferCredentials credentials) {
        this.credentials = credentials;
        this.minio = MinioClient.builder()
                .endpoint(credentials.minioEndpoint())
                .credentialsProvider(new StaticProvider(credentials.accessKey(), credentials.secretKey(), credentials.sessionToken()))
                .build();
    }
}
