/*
 * Classe de configuração do MinIO para armazenamento de objetos.
 * Inicializa e configura o bean MinioClient utilizando propriedades de conexão,
 * permitindo que o sistema interaja com o serviço MinIO ou S3 compatível.
 *
 * VULN-004: OkHttpClient com timeouts configuráveis para evitar thread starvation
 * quando o MinIO estiver lento ou indisponível.
 */
package com.keeply.backend.config;

import io.minio.MinioClient;
import okhttp3.OkHttpClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration
public class MinioConfig {

    @Bean
    MinioClient minioClient(
            @Value("${keeply.minio.endpoint}") String endpoint,
            @Value("${keeply.minio.access-key}") String accessKey,
            @Value("${keeply.minio.secret-key}") String secretKey,
            // VULN-004: timeouts configuráveis via env — evita bloqueio indefinido de threads
            @Value("${keeply.minio.connect-timeout-seconds:5}") long connectTimeoutSeconds,
            @Value("${keeply.minio.write-timeout-seconds:30}") long writeTimeoutSeconds,
            @Value("${keeply.minio.read-timeout-seconds:120}") long readTimeoutSeconds
    ) {
        OkHttpClient httpClient = new OkHttpClient.Builder()
                .connectTimeout(connectTimeoutSeconds, TimeUnit.SECONDS)
                .writeTimeout(writeTimeoutSeconds, TimeUnit.SECONDS)
                .readTimeout(readTimeoutSeconds, TimeUnit.SECONDS)
                .build();

        return MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .httpClient(httpClient)
                .build();
    }
}
