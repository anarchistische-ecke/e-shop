package com.example.api.catalog;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.ObjectCannedACL;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.util.Locale;
import java.util.UUID;

@Service
public class CategoryImageStorageService {
    private static final String DEFAULT_PUBLIC_BASE_URL = "https://storage.yandexcloud.net";

    private final S3Client s3Client;
    private final String bucketName;
    private final String publicBaseUrl;

    public CategoryImageStorageService(
            S3Client s3Client,
            @Value("${yandex.storage.bucket}") String bucketName,
            @Value("${yandex.storage.public-base-url:${yandex.storage.endpoint:https://storage.yandexcloud.net}}") String publicBaseUrl) {
        this.s3Client = s3Client;
        this.bucketName = bucketName;
        this.publicBaseUrl = normalizeBaseUrl(publicBaseUrl);
    }

    public StoredImage upload(UUID categoryId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Файл изображения отсутствует");
        }
        if (bucketName == null || bucketName.isBlank()) {
            throw new IllegalStateException("Yandex Object Storage bucket is not configured");
        }
        String extension = extractExtension(file.getOriginalFilename());
        String key = String.format("categories/%s/%s%s", categoryId, UUID.randomUUID(), extension);
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .contentType(file.getContentType() != null ? file.getContentType() : "application/octet-stream")
                .cacheControl("public, max-age=31536000, immutable")
                .acl(ObjectCannedACL.PUBLIC_READ)
                .build();
        try {
            s3Client.putObject(request, RequestBody.fromInputStream(file.getInputStream(), file.getSize()));
        } catch (IOException e) {
            throw new IllegalArgumentException("Не удалось прочитать файл изображения", e);
        }
        return new StoredImage(key, publicUrlFor(key));
    }

    private String extractExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }
        String ext = filename.substring(filename.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        return ext.isBlank() ? "" : "." + ext;
    }

    private String normalizeBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return DEFAULT_PUBLIC_BASE_URL;
        }
        String trimmed = baseUrl.trim();
        return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }

    private String publicUrlFor(String key) {
        String base = publicBaseUrl;
        if (base.endsWith("/" + bucketName)) {
            return base + "/" + key;
        }
        return base + "/" + bucketName + "/" + key;
    }

    public record StoredImage(String objectKey, String url) {
    }
}
