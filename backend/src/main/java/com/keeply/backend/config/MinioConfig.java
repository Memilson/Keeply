package com.keeply.backend.config;

import io.minio.MinioClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MinioConfig {
    @Bean
    MinioClient minioClient(
            @Value("${keeply.minio.endpoint}") String endpoint,
            @Value("${keeply.minio.access-key}") String accessKey,
            @Value("${keeply.minio.secret-key}") String secretKey
    ) {
        return MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
    }
}
