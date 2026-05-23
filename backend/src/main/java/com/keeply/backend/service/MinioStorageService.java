/* Implementação do serviço de armazenamento de objetos que utiliza a infraestrutura do MinIO para persistir dados. */
package com.keeply.backend.service;

import io.minio.*;
import io.minio.errors.ErrorResponseException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;

@Service
public class MinioStorageService implements ObjectStorageService {
    private final MinioClient minio;
    private final String bucket;

    public MinioStorageService(MinioClient minio, @Value("${keeply.minio.bucket}") String bucket) {
        this.minio = minio;
        this.bucket = bucket;
        ensureBucket();
    }

    private void ensureBucket() {
        try {
            boolean exists = minio.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
            if (!exists) {
                minio.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
            }
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao preparar bucket MinIO", e);
        }
    }

    @Override
    public void put(String key, byte[] data, String contentType) {
        try {
            minio.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(key)
                    .stream(new ByteArrayInputStream(data), data.length, -1)
                    .contentType(contentType)
                    .build());
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao salvar objeto no MinIO: " + key, e);
        }
    }

    @Override
    public byte[] get(String key) {
        try (var in = minio.getObject(GetObjectArgs.builder().bucket(bucket).object(key).build())) {
            return in.readAllBytes();
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao ler objeto do MinIO: " + key, e);
        }
    }

    @Override
    public void delete(String key) {
        try {
            minio.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(key).build());
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao remover objeto do MinIO: " + key, e);
        }
    }

    @Override
    public boolean exists(String key) {
        try {
            minio.statObject(StatObjectArgs.builder().bucket(bucket).object(key).build());
            return true;
        } catch (ErrorResponseException e) {
            return false;
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao verificar objeto no MinIO: " + key, e);
        }
    }
}
