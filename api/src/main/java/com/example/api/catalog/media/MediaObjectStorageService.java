package com.example.api.catalog.media;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.AbortMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CompleteMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CompletedMultipartUpload;
import software.amazon.awssdk.services.s3.model.CompletedPart;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.Delete;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.ObjectCannedACL;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.UploadPartRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.UploadPartPresignRequest;
import software.amazon.awssdk.utils.http.SdkHttpUtils;

import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class MediaObjectStorageService {
    private final S3Client s3;
    private final S3Presigner presigner;
    private final MediaUploadProperties properties;
    private final MediaDerivativeConfig derivativeConfig;
    private final String originalBucket;
    private final String originalPublicBaseUrl;

    public MediaObjectStorageService(
            S3Client s3,
            S3Presigner presigner,
            MediaUploadProperties properties,
            MediaDerivativeConfig derivativeConfig,
            @Value("${yandex.storage.bucket}") String originalBucket,
            @Value("${yandex.storage.public-base-url:${yandex.storage.endpoint:https://storage.yandexcloud.net}}")
            String originalPublicBaseUrl
    ) {
        this.s3 = s3;
        this.presigner = presigner;
        this.properties = properties;
        this.derivativeConfig = derivativeConfig;
        this.originalBucket = originalBucket;
        this.originalPublicBaseUrl = normalizeBaseUrl(originalPublicBaseUrl);
    }

    public PresignedRequest presignSinglePut(String key, String contentType, long contentLength) {
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(originalBucket)
                .key(key)
                .contentType(contentType)
                .contentLength(contentLength)
                .build();
        var presigned = presigner.presignPutObject(PutObjectPresignRequest.builder()
                .signatureDuration(properties.getUrlTtl())
                .putObjectRequest(request)
                .build());
        return new PresignedRequest(presigned.url().toString(), flattenHeaders(presigned.signedHeaders()));
    }

    public String createMultipartUpload(String key, String contentType) {
        return s3.createMultipartUpload(CreateMultipartUploadRequest.builder()
                        .bucket(originalBucket)
                        .key(key)
                        .contentType(contentType)
                        .build())
                .uploadId();
    }

    public PresignedRequest presignPart(String key, String uploadId, int partNumber, long contentLength) {
        UploadPartRequest request = UploadPartRequest.builder()
                .bucket(originalBucket)
                .key(key)
                .uploadId(uploadId)
                .partNumber(partNumber)
                .contentLength(contentLength)
                .build();
        var presigned = presigner.presignUploadPart(UploadPartPresignRequest.builder()
                .signatureDuration(properties.getUrlTtl())
                .uploadPartRequest(request)
                .build());
        return new PresignedRequest(presigned.url().toString(), flattenHeaders(presigned.signedHeaders()));
    }

    public void completeMultipart(String key, String uploadId, List<MediaUploadModels.CompletePart> parts) {
        List<CompletedPart> completedParts = parts.stream()
                .map(part -> CompletedPart.builder()
                        .partNumber(part.partNumber())
                        .eTag(normalizeEtag(part.eTag()))
                        .build())
                .toList();
        s3.completeMultipartUpload(CompleteMultipartUploadRequest.builder()
                .bucket(originalBucket)
                .key(key)
                .uploadId(uploadId)
                .multipartUpload(CompletedMultipartUpload.builder().parts(completedParts).build())
                .build());
    }

    public void abortMultipart(String key, String uploadId) {
        if (!StringUtils.hasText(uploadId)) {
            return;
        }
        try {
            s3.abortMultipartUpload(AbortMultipartUploadRequest.builder()
                    .bucket(originalBucket)
                    .key(key)
                    .uploadId(uploadId)
                    .build());
        } catch (S3Exception error) {
            if (error.statusCode() != 404) {
                throw error;
            }
        }
    }

    public HeadObjectResponse headOriginal(String key) {
        return s3.headObject(HeadObjectRequest.builder().bucket(originalBucket).key(key).build());
    }

    public void verifyBucketsAccessible() {
        s3.headBucket(HeadBucketRequest.builder().bucket(originalBucket).build());
        s3.headBucket(HeadBucketRequest.builder().bucket(requireDerivativeBucket()).build());
    }

    public boolean originalExists(String key) {
        try {
            headOriginal(key);
            return true;
        } catch (RuntimeException error) {
            return false;
        }
    }

    public void downloadOriginal(String key, Path destination) {
        s3.getObject(builder -> builder.bucket(originalBucket).key(key), ResponseTransformer.toFile(destination));
    }

    public void copyPendingToFinal(String pendingKey, String finalKey, String contentType) {
        if (originalExists(finalKey)) {
            return;
        }
        s3.copyObject(CopyObjectRequest.builder()
                .bucket(originalBucket)
                .copySource(SdkHttpUtils.urlEncodeIgnoreSlashes(originalBucket + "/" + pendingKey))
                .key(finalKey)
                .contentType(contentType)
                .cacheControl(derivativeConfig.cacheControl())
                .acl(ObjectCannedACL.PUBLIC_READ)
                .metadataDirective("REPLACE")
                .build());
    }

    public void uploadDerivative(String key, String contentType, Path path) {
        if (derivativeExists(key)) {
            return;
        }
        s3.putObject(PutObjectRequest.builder()
                        .bucket(requireDerivativeBucket())
                        .key(key)
                        .contentType(contentType)
                        .cacheControl(derivativeConfig.cacheControl())
                        .acl(ObjectCannedACL.PUBLIC_READ)
                        .build(),
                RequestBody.fromFile(path));
    }

    public boolean derivativeExists(String key) {
        try {
            s3.headObject(HeadObjectRequest.builder()
                    .bucket(requireDerivativeBucket())
                    .key(key)
                    .build());
            return true;
        } catch (RuntimeException error) {
            return false;
        }
    }

    public void deleteOriginal(String key) {
        if (!StringUtils.hasText(key)) {
            return;
        }
        s3.deleteObject(DeleteObjectRequest.builder().bucket(originalBucket).key(key).build());
    }

    public void deleteOriginalAndDerivatives(String objectKey) {
        if (!StringUtils.hasText(objectKey)) {
            return;
        }
        deleteOriginal(objectKey);
        List<ObjectIdentifier> identifiers = derivativeKeys(objectKey).stream()
                .map(key -> ObjectIdentifier.builder().key(key).build())
                .toList();
        if (!identifiers.isEmpty()) {
            s3.deleteObjects(DeleteObjectsRequest.builder()
                    .bucket(requireDerivativeBucket())
                    .delete(Delete.builder().objects(identifiers).quiet(true).build())
                    .build());
        }
    }

    public List<String> derivativeKeys(String objectKey) {
        String base = stripExtension(objectKey);
        List<String> keys = new ArrayList<>();
        for (Integer width : derivativeConfig.widths()) {
            for (String format : derivativeConfig.formats().keySet()) {
                keys.add(normalizeToken(properties.getDerivativePathPrefix())
                        + "/" + base + "/w" + width + "." + format);
            }
        }
        return keys;
    }

    public String derivativeKey(String objectKey, int width, String format) {
        return normalizeToken(properties.getDerivativePathPrefix())
                + "/" + stripExtension(objectKey)
                + "/w" + width + "." + format;
    }

    public String publicOriginalUrl(String key) {
        if (originalPublicBaseUrl.endsWith("/" + originalBucket)) {
            return originalPublicBaseUrl + "/" + key;
        }
        return originalPublicBaseUrl + "/" + originalBucket + "/" + key;
    }

    public String extractManagedObjectKey(String url) {
        if (!StringUtils.hasText(url)) {
            return null;
        }
        String path;
        try {
            path = URI.create(url.trim()).getPath();
        } catch (IllegalArgumentException error) {
            return null;
        }
        String bucketMarker = "/" + originalBucket + "/";
        int bucketIndex = path.indexOf(bucketMarker);
        if (bucketIndex >= 0) {
            return path.substring(bucketIndex + bucketMarker.length());
        }
        for (String marker : List.of("/products/", "/categories/")) {
            int index = path.indexOf(marker);
            if (index >= 0) {
                return path.substring(index + 1);
            }
        }
        return null;
    }

    private String requireDerivativeBucket() {
        if (!StringUtils.hasText(properties.getDerivativeBucket())) {
            throw new IllegalStateException("MEDIA_DERIVATIVES_BUCKET is not configured");
        }
        return properties.getDerivativeBucket().trim();
    }

    private Map<String, String> flattenHeaders(Map<String, List<String>> headers) {
        Map<String, String> flattened = new LinkedHashMap<>();
        headers.forEach((name, values) -> {
            if (!name.equalsIgnoreCase("host") && values != null && !values.isEmpty()) {
                flattened.put(name, String.join(",", values));
            }
        });
        return flattened;
    }

    private String normalizeEtag(String eTag) {
        String trimmed = eTag.trim();
        return trimmed.startsWith("\"") && trimmed.endsWith("\"")
                ? trimmed.substring(1, trimmed.length() - 1)
                : trimmed;
    }

    private String stripExtension(String key) {
        int extensionIndex = key.lastIndexOf('.');
        return extensionIndex > key.lastIndexOf('/') ? key.substring(0, extensionIndex) : key;
    }

    private String normalizeBaseUrl(String value) {
        String trimmed = value == null ? "" : value.trim();
        return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }

    private String normalizeToken(String value) {
        if (!StringUtils.hasText(value)) {
            return "media";
        }
        return value.trim().replaceAll("^/+", "").replaceAll("/+$", "");
    }

    public record PresignedRequest(String url, Map<String, String> headers) {
    }
}
