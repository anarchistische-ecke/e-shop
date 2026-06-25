package com.example.api.catalog;

import com.example.api.catalog.media.MediaObjectStorageService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.ObjectCannedACL;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.util.Locale;
import java.util.UUID;

@Service
public class ProductImageStorageService {
    private static final String DEFAULT_PUBLIC_BASE_URL = "https://storage.yandexcloud.net";

    private final S3Client s3Client;
    private final String bucketName;
    private final String publicBaseUrl;
    private final MediaObjectStorageService mediaObjectStorageService;

    public ProductImageStorageService(
            S3Client s3Client,
            @Value("${yandex.storage.bucket}") String bucketName,
            @Value("${yandex.storage.public-base-url:${yandex.storage.endpoint:https://storage.yandexcloud.net}}") String publicBaseUrl,
            MediaObjectStorageService mediaObjectStorageService) {
        this.s3Client = s3Client;
        this.bucketName = bucketName;
        this.publicBaseUrl = normalizeBaseUrl(publicBaseUrl);
        this.mediaObjectStorageService = mediaObjectStorageService;
    }

    public StoredImage upload(UUID productId, MultipartFile file, int position) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Файл изображения отсутствует");
        }
        if (bucketName == null || bucketName.isBlank()) {
            throw new IllegalStateException("Yandex Object Storage bucket is not configured");
        }
        String extension = extractExtension(file.getOriginalFilename());
        String key = String.format("products/%s/%s%s", productId, UUID.randomUUID(), extension);
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
        return new StoredImage(key, publicUrlFor(key), position);
    }

    public StoredImageContent download(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            throw new IllegalArgumentException("Image object key is required");
        }
        if (bucketName == null || bucketName.isBlank()) {
            throw new IllegalStateException("Yandex Object Storage bucket is not configured");
        }
        ResponseBytes<GetObjectResponse> response = s3Client.getObjectAsBytes(GetObjectRequest.builder()
                .bucket(bucketName)
                .key(objectKey)
                .build());
        return new StoredImageContent(response.asByteArray(), response.response().contentType());
    }

    public void delete(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            return;
        }
        if (bucketName == null || bucketName.isBlank()) {
            return;
        }
        mediaObjectStorageService.deleteOriginalAndDerivatives(objectKey);
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

    public record StoredImage(String objectKey, String url, int position) {
    }

    public record StoredImageContent(byte[] bytes, String contentType) {
    }
}
