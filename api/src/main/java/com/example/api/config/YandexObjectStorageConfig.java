package com.example.api.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

import java.net.URI;

@Configuration
public class YandexObjectStorageConfig {
    @Value("${yandex.storage.bucket}")
    private String bucketName;
    @Value("${yandex.storage.access-key}")
    private String accessKey;
    @Value("${yandex.storage.secret-key}")
    private String secretKey;
    @Value("${yandex.storage.endpoint:https://storage.yandexcloud.net}")
    private String endpoint;

    @Bean
    public S3Client yandexS3Client() {
        if (accessKey == null || accessKey.isBlank() || secretKey == null || secretKey.isBlank()) {
            throw new IllegalStateException("Yandex Object Storage credentials are not configured. Set YANDEX_STORAGE_KEY and YANDEX_STORAGE_SECRET.");
        }
        AwsBasicCredentials creds = AwsBasicCredentials.create(accessKey, secretKey);
        return S3Client.builder()
                .region(Region.of("ru-central1"))
                .endpointOverride(URI.create(endpoint))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .credentialsProvider(StaticCredentialsProvider.create(creds))
                .build();
    }

    @Bean
    public String yandexBucketName() {
        return bucketName;
    }
}
