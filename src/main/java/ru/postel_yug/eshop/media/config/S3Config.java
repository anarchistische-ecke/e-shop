package ru.postel_yug.eshop.media.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class S3Config {
    @Value("${app.storage.s3.accessKey}")
    private String accessKey;
    @Value("${app.storage.s3.secretKey}")
    private String secretKey;
    @Value("${app.storage.s3.bucket}")
    private String bucket;
    @Value("${app.storage.s3.region:ru-central1}")
    private String region;
    @Bean
    public S3Client s3client() {
        AwsCredentials creds = AwsBasicCredentials.create(accessKey, secretKey);
        return S3Client.builder()
                .credentialsProvider(StaticCredentialsProvider.create(creds))
                .endpointOverride(URI.create("https://storage.yandexcloud.net"))
                .region(Region.of(region))
                .build();
    }
    @Bean
    public String defaultBucketName() {
        return bucket;
    }
}

