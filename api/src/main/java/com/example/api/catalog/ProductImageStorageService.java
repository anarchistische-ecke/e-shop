package com.example.api.catalog;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.ObjectCannedACL;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.util.Locale;
import java.util.UUID;

@Service
public class ProductImageStorageService {
    private final S3Client s3Client;
    private final String bucketName;

    public ProductImageStorageService(S3Client s3Client, @Value("${yandex.storage.bucket}") String bucketName) {
        this.s3Client = s3Client;
        this.bucketName = bucketName;
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
                .acl(ObjectCannedACL.PUBLIC_READ)
                .build();
        try {
            s3Client.putObject(request, RequestBody.fromBytes(file.getBytes()));
        } catch (IOException e) {
            throw new IllegalArgumentException("Не удалось прочитать файл изображения", e);
        }
        return new StoredImage(key, publicUrlFor(key), position);
    }

    public void delete(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            return;
        }
        if (bucketName == null || bucketName.isBlank()) {
            return;
        }
        DeleteObjectRequest request = DeleteObjectRequest.builder()
                .bucket(bucketName)
                .key(objectKey)
                .build();
        s3Client.deleteObject(request);
    }

    private String extractExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }
        String ext = filename.substring(filename.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        return ext.isBlank() ? "" : "." + ext;
    }

    private String publicUrlFor(String key) {
        return "https://storage.yandexcloud.net/" + bucketName + "/" + key;
    }

    public record StoredImage(String objectKey, String url, int position) {
    }
}
