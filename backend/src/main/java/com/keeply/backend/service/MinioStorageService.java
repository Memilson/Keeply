/* Implementação do serviço de armazenamento de objetos que utiliza a infraestrutura do MinIO para persistir dados. */
package com.keeply.backend.service;

import io.minio.*;
import io.minio.errors.ErrorResponseException;
import io.minio.messages.DeleteObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class MinioStorageService implements ObjectStorageService {
    private static final Logger log = LoggerFactory.getLogger(MinioStorageService.class);
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
                log.info("Criando bucket MinIO: {}", bucket);
                minio.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
            }
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao preparar bucket MinIO", e);
        }
    }

    @Override
    public void put(String key, java.io.InputStream data, long length, String contentType) {
        try {
            log.debug("Armazenando objeto no MinIO: {} (Tamanho: {}, Tipo: {})", key, length, contentType);
            minio.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(key)
                    .stream(data, length, -1)
                    .contentType(contentType)
                    .build());
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao salvar objeto no MinIO: " + key, e);
        }
    }

    @Override
    public java.io.InputStream getStream(String key) {
        try {
            return minio.getObject(GetObjectArgs.builder().bucket(bucket).object(key).build());
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao abrir stream do objeto no MinIO: " + key, e);
        }
    }

    @Override
    public void delete(String key) {
        try {
            log.debug("Removendo objeto do MinIO: {}", key);
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

    @Override
    public void copy(String sourceKey, String destinationKey) {
        try {
            minio.copyObject(CopyObjectArgs.builder()
                    .bucket(bucket)
                    .object(destinationKey)
                    .source(CopySource.builder().bucket(bucket).object(sourceKey).build())
                    .build());
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao promover objeto MinIO: " + sourceKey, e);
        }
    }

    @Override
    public void deletePrefix(String prefix) {
        try {
            java.util.List<DeleteObject> batch = new java.util.ArrayList<>(1000);
            for (Result<io.minio.messages.Item> result : minio.listObjects(
                    ListObjectsArgs.builder().bucket(bucket).prefix(prefix).recursive(true).build())) {
                batch.add(new DeleteObject(result.get().objectName()));
                if (batch.size() == 1000) {
                    removeBatch(batch);
                    batch.clear();
                }
            }
            if (!batch.isEmpty()) {
                removeBatch(batch);
            }
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao limpar staging MinIO: " + prefix, e);
        }
    }

    private void removeBatch(java.util.List<DeleteObject> objects) throws Exception {
        for (Result<io.minio.messages.DeleteError> result : minio.removeObjects(
                RemoveObjectsArgs.builder().bucket(bucket).objects(new java.util.ArrayList<>(objects)).build())) {
            io.minio.messages.DeleteError error = result.get();
            throw new IllegalStateException("Falha ao remover objeto MinIO: " + error.objectName());
        }
    }
}
