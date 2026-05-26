package com.keeply.backend.service;

import io.minio.credentials.AssumeRoleProvider;
import io.minio.credentials.Credentials;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

@Service
public class MinioStsCredentialIssuer implements TemporaryCredentialIssuer {
    private static final Logger log = LoggerFactory.getLogger(MinioStsCredentialIssuer.class);
    private final String endpoint;
    private final String accessKey;
    private final String secretKey;

    public MinioStsCredentialIssuer(
            @Value("${keeply.minio.endpoint}") String endpoint,
            @Value("${keeply.minio.access-key}") String accessKey,
            @Value("${keeply.minio.secret-key}") String secretKey
    ) {
        this.endpoint = endpoint;
        this.accessKey = accessKey;
        this.secretKey = secretKey;
    }

    @Override
    public IssuedCredential issue(String policy, Instant expiresAt) {
        try {
            int durationSeconds = Math.toIntExact(Math.max(1, Duration.between(Instant.now(), expiresAt).toSeconds()));
            Credentials credentials = new AssumeRoleProvider(
                    endpoint, accessKey, secretKey, durationSeconds, policy,
                    null, null, null, null, null
            ).fetch();
            return new IssuedCredential(credentials.accessKey(), credentials.secretKey(), credentials.sessionToken());
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao emitir credencial temporária MinIO", e);
        }
    }

    @Override
    public void revoke(String accessKey) {
        // STS policies are restricted and expire automatically. A MinIO admin issuer can replace
        // this implementation when immediate server-side revocation is available in deployment.
        log.debug("Credencial STS encerrada no gateway; access key aguarda expiração MinIO: {}", accessKey);
    }
}
