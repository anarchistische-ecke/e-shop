package ru.postel_yug.eshop.media.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Service
public class MediaService {
    @Autowired
    private S3Client s3;
    @Autowired private String defaultBucketName;

    public URL uploadMedia(String key, InputStream fileStream, String contentType, long size) throws IOException {
        PutObjectRequest req = PutObjectRequest.builder()
                .bucket(defaultBucketName)
                .key(key)
                .contentType(contentType)
                .acl("public-read")  // делаем объект публично доступным (либо настроить политику бакета)
                .build();
        s3.putObject(req, RequestBody.fromInputStream(fileStream, size));
        // Формируем URL к загруженному объекту
        return getPublicUrl(key);
    }

    public URL getPublicUrl(String key) {
        // Yandex Object Storage публичный URL формируется как https://storage.yandexcloud.net/<bucket>/<key>
        try {
            return new URL("https://storage.yandexcloud.net/" + defaultBucketName + "/" + URLEncoder.encode(key, StandardCharsets.UTF_8));
        } catch(Exception e) {
            throw new RuntimeException(e);
        }
    }

    public byte[] downloadMedia(String key) throws IOException {
        GetObjectRequest req = GetObjectRequest.builder().bucket(defaultBucketName).key(key).build();
        ResponseBytes<GetObjectResponse> resp = s3.getObjectAsBytes(req);
        return resp.asByteArray();
    }

    public void deleteMedia(String key) {
        DeleteObjectRequest req = DeleteObjectRequest.builder().bucket(defaultBucketName).key(key).build();
        s3.deleteObject(req);
    }
}

