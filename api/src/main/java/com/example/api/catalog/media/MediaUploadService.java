package com.example.api.catalog.media;

import com.example.api.catalog.CategoryController;
import com.example.api.catalog.DirectusBridgeSecurity;
import com.example.catalog.domain.Category;
import com.example.catalog.service.CatalogService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class MediaUploadService {
    private static final Logger log = LoggerFactory.getLogger(MediaUploadService.class);
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("image/jpeg", "image/png", "image/webp");

    private final CatalogService catalogService;
    private final MediaUploadProperties properties;
    private final MediaUploadStateStore stateStore;
    private final MediaObjectStorageService storage;
    private final MediaDerivativeProcessor derivativeProcessor;
    private final MediaUploadFeatureFlag featureFlag;

    public MediaUploadService(
            CatalogService catalogService,
            MediaUploadProperties properties,
            MediaUploadStateStore stateStore,
            MediaObjectStorageService storage,
            MediaDerivativeProcessor derivativeProcessor,
            MediaUploadFeatureFlag featureFlag
    ) {
        this.catalogService = catalogService;
        this.properties = properties;
        this.stateStore = stateStore;
        this.storage = storage;
        this.derivativeProcessor = derivativeProcessor;
        this.featureFlag = featureFlag;
    }

    public MediaUploadModels.CreateBatchResponse createBatch(
            MediaUploadModels.CreateBatchRequest request,
            DirectusBridgeSecurity.DirectusBridgePrincipal principal
    ) {
        if (!featureFlag.isEnabled()) {
            throw new IllegalStateException("Direct image uploads are temporarily disabled");
        }
        if (request.files().size() > 50) {
            throw new IllegalArgumentException("A maximum of 50 images can be uploaded in one batch");
        }
        validateTarget(request.targetType(), request.entityId());
        request.files().forEach(file -> {
            validateFile(file);
            validateVariant(request.targetType(), request.entityId(), file.variantId());
        });
        UUID batchId = UUID.randomUUID();
        int defaultPosition = request.targetType() == MediaUploadModels.TargetType.PRODUCT
                ? catalogService.getProductImages(request.entityId()).size()
                : 0;
        List<MediaUploadModels.UploadItemResponse> responses = new ArrayList<>();
        int offset = 0;
        for (MediaUploadModels.CreateFileRequest file : request.files()) {
            MediaUploadRecord record = new MediaUploadRecord();
            UUID uploadId = UUID.randomUUID();
            String contentType = normalizeContentType(file.contentType());
            String extension = extensionFor(contentType);
            MediaUploadModels.UploadMethod method = file.size() <= properties.getSinglePutThreshold().toBytes()
                    ? MediaUploadModels.UploadMethod.SINGLE
                    : MediaUploadModels.UploadMethod.MULTIPART;
            int position = file.position() != null ? Math.max(0, file.position()) : defaultPosition + offset;
            String pendingKey = normalizePrefix(properties.getPendingPrefix())
                    + "/" + batchId + "/" + uploadId + "." + extension;
            String finalKey = targetPrefix(request.targetType())
                    + "/" + request.entityId() + "/" + uploadId + "." + extension;

            record.setId(uploadId);
            record.setBatchId(batchId);
            record.setTargetType(request.targetType());
            record.setEntityId(request.entityId());
            record.setVariantId(file.variantId());
            record.setPosition(position);
            record.setFilename(file.filename().trim());
            record.setContentType(contentType);
            record.setExpectedSize(file.size());
            record.setUploadMethod(method);
            record.setStatus(MediaUploadModels.Status.UPLOADING);
            record.setPendingKey(pendingKey);
            record.setFinalKey(finalKey);
            record.setActor(principal.actor());
            record.setCreatedAt(OffsetDateTime.now());
            if (method == MediaUploadModels.UploadMethod.MULTIPART) {
                record.setStorageUploadId(storage.createMultipartUpload(pendingKey, contentType));
            }
            stateStore.create(record);

            MediaObjectStorageService.PresignedRequest singlePut = method == MediaUploadModels.UploadMethod.SINGLE
                    ? storage.presignSinglePut(pendingKey, contentType, file.size())
                    : null;
            responses.add(toResponse(record, singlePut));
            offset++;
        }
        return new MediaUploadModels.CreateBatchResponse(
                batchId,
                featureFlag.isEnabled(),
                properties.getMaxFileSize().toBytes(),
                properties.getMaxPixels(),
                properties.getSinglePutThreshold().toBytes(),
                properties.getMultipartPartSize().toBytes(),
                responses
        );
    }

    public boolean uploadsEnabled() {
        return featureFlag.isEnabled();
    }

    public boolean setUploadsEnabled(boolean enabled) {
        return featureFlag.setEnabled(enabled);
    }

    public MediaUploadModels.SignPartsResponse signParts(UUID uploadId, MediaUploadModels.SignPartsRequest request) {
        MediaUploadRecord record = stateStore.require(uploadId);
        requireStatus(record, MediaUploadModels.Status.UPLOADING);
        if (record.getUploadMethod() != MediaUploadModels.UploadMethod.MULTIPART) {
            throw new IllegalArgumentException("Upload does not use multipart transfer");
        }
        int totalParts = totalParts(record);
        List<MediaUploadModels.SignedPart> parts = request.partNumbers().stream()
                .distinct()
                .sorted()
                .map(partNumber -> {
                    if (partNumber < 1 || partNumber > totalParts) {
                        throw new IllegalArgumentException("Invalid multipart part number: " + partNumber);
                    }
                    long partLength = partLength(record, partNumber);
                    var signed = storage.presignPart(
                            record.getPendingKey(),
                            record.getStorageUploadId(),
                            partNumber,
                            partLength
                    );
                    return new MediaUploadModels.SignedPart(partNumber, signed.url(), signed.headers());
                })
                .toList();
        return new MediaUploadModels.SignPartsResponse(uploadId, parts);
    }

    public MediaUploadModels.RenewSingleResponse renewSingle(UUID uploadId) {
        MediaUploadRecord record = stateStore.require(uploadId);
        requireStatus(record, MediaUploadModels.Status.UPLOADING);
        if (record.getUploadMethod() != MediaUploadModels.UploadMethod.SINGLE) {
            throw new IllegalArgumentException("Upload uses multipart transfer");
        }
        var signed = storage.presignSinglePut(
                record.getPendingKey(),
                record.getContentType(),
                record.getExpectedSize()
        );
        return new MediaUploadModels.RenewSingleResponse(uploadId, signed.url(), signed.headers());
    }

    public MediaUploadModels.UploadItemResponse complete(
            UUID uploadId,
            MediaUploadModels.CompleteUploadRequest request
    ) {
        MediaUploadRecord record = stateStore.require(uploadId);
        if (record.getStatus() == MediaUploadModels.Status.QUEUED
                || record.getStatus() == MediaUploadModels.Status.PROCESSING
                || record.getStatus() == MediaUploadModels.Status.READY) {
            return toResponse(record, null);
        }
        requireStatus(record, MediaUploadModels.Status.UPLOADING);
        if (record.getUploadMethod() == MediaUploadModels.UploadMethod.MULTIPART) {
            List<MediaUploadModels.CompletePart> parts = request != null && request.parts() != null
                    ? request.parts()
                    : List.of();
            validateCompletedParts(record, parts);
            if (!storage.originalExists(record.getPendingKey())) {
                storage.completeMultipart(record.getPendingKey(), record.getStorageUploadId(), parts);
            }
        }
        HeadObjectResponse head = storage.headOriginal(record.getPendingKey());
        if (head.contentLength() != record.getExpectedSize()) {
            throw new IllegalArgumentException("Uploaded object size does not match the selected file");
        }
        record.setStatus(MediaUploadModels.Status.QUEUED);
        record.setError(null);
        stateStore.save(record);
        stateStore.enqueue(record.getId());
        return toResponse(record, null);
    }

    public MediaUploadModels.UploadItemResponse retry(UUID uploadId) {
        MediaUploadRecord record = stateStore.require(uploadId);
        requireStatus(record, MediaUploadModels.Status.FAILED);
        if (!storage.originalExists(record.getPendingKey()) && !storage.originalExists(record.getFinalKey())) {
            throw new IllegalStateException("The uploaded original is no longer available; select the file again");
        }
        record.setStatus(MediaUploadModels.Status.QUEUED);
        record.setError(null);
        stateStore.save(record);
        stateStore.enqueue(record.getId());
        return toResponse(record, null);
    }

    public MediaUploadModels.UploadItemResponse abort(UUID uploadId) {
        MediaUploadRecord record = stateStore.require(uploadId);
        if (record.getStatus() == MediaUploadModels.Status.READY) {
            throw new IllegalStateException("A completed image cannot be aborted");
        }
        if (record.getStatus() == MediaUploadModels.Status.PROCESSING) {
            throw new IllegalStateException("An image being optimized cannot be aborted");
        }
        if (record.getStatus() == MediaUploadModels.Status.ABORTED
                || record.getStatus() == MediaUploadModels.Status.EXPIRED) {
            return toResponse(record, null);
        }
        if (isAttached(record)) {
            record.setStatus(MediaUploadModels.Status.READY);
            record.setError(null);
            stateStore.save(record);
            return toResponse(record, null);
        }
        if (record.getUploadMethod() == MediaUploadModels.UploadMethod.MULTIPART
                && record.getStatus() == MediaUploadModels.Status.UPLOADING) {
            storage.abortMultipart(record.getPendingKey(), record.getStorageUploadId());
        }
        storage.deleteOriginal(record.getPendingKey());
        if (storage.originalExists(record.getFinalKey())) {
            storage.deleteOriginalAndDerivatives(record.getFinalKey());
        }
        record.setStatus(MediaUploadModels.Status.ABORTED);
        record.setError(null);
        stateStore.save(record);
        return toResponse(record, null);
    }

    public MediaUploadModels.UploadBatchStatus batchStatus(UUID batchId) {
        List<MediaUploadRecord> records = stateStore.getBatch(batchId);
        if (records.isEmpty()) {
            throw new IllegalArgumentException("Media upload batch was not found: " + batchId);
        }
        return new MediaUploadModels.UploadBatchStatus(
                batchId,
                records.stream().map(record -> toResponse(record, null)).toList()
        );
    }

    public List<MediaUploadModels.UploadItemResponse> pending(
            MediaUploadModels.TargetType targetType,
            UUID entityId
    ) {
        return stateStore.listPending().stream()
                .filter(record -> targetType == null || record.getTargetType() == targetType)
                .filter(record -> entityId == null || entityId.equals(record.getEntityId()))
                .map(record -> toResponse(record, null))
                .toList();
    }

    public void expireStaleUploads() {
        OffsetDateTime now = OffsetDateTime.now();
        for (MediaUploadRecord record : stateStore.listPending()) {
            if (record.getStatus() == MediaUploadModels.Status.UPLOADING
                    && record.getCreatedAt().plus(properties.getSessionTtl()).isBefore(now)) {
                try {
                    if (record.getUploadMethod() == MediaUploadModels.UploadMethod.MULTIPART) {
                        storage.abortMultipart(record.getPendingKey(), record.getStorageUploadId());
                    }
                    storage.deleteOriginal(record.getPendingKey());
                } catch (RuntimeException error) {
                    log.warn("Could not clean expired media upload {}", record.getId(), error);
                }
                record.setStatus(MediaUploadModels.Status.EXPIRED);
                record.setError("Upload session expired");
                stateStore.save(record);
            }
        }
    }

    public void recoverInterruptedProcessing() {
        for (MediaUploadRecord record : stateStore.listPending()) {
            if (record.getStatus() == MediaUploadModels.Status.PROCESSING) {
                record.setStatus(MediaUploadModels.Status.QUEUED);
                record.setError("Recovered after an interrupted processing attempt");
                stateStore.save(record);
                stateStore.enqueue(record.getId());
            }
        }
    }

    public void process(UUID uploadId) {
        MediaUploadRecord record = stateStore.get(uploadId);
        if (record == null || record.getStatus() != MediaUploadModels.Status.QUEUED) {
            return;
        }
        record.setStatus(MediaUploadModels.Status.PROCESSING);
        record.setAttemptCount(record.getAttemptCount() + 1);
        record.setError(null);
        stateStore.save(record);

        Path workDirectory = null;
        try {
            workDirectory = Files.createTempDirectory("catalogue-media-" + record.getId() + "-");
            Path source = workDirectory.resolve("source");
            String sourceKey = storage.originalExists(record.getPendingKey())
                    ? record.getPendingKey()
                    : record.getFinalKey();
            HeadObjectResponse head = storage.headOriginal(sourceKey);
            if (head.contentLength() != record.getExpectedSize()) {
                throw new IllegalArgumentException("Uploaded object size does not match the selected file");
            }
            storage.downloadOriginal(sourceKey, source);
            MediaDerivativeProcessor.ProcessedImage processed = derivativeProcessor.process(
                    source,
                    workDirectory.resolve("derivatives")
            );
            if (!normalizeContentType(processed.contentType()).equals(record.getContentType())) {
                throw new IllegalArgumentException("Uploaded image type does not match the selected file");
            }
            record.setWidth(processed.width());
            record.setHeight(processed.height());
            storage.copyPendingToFinal(sourceKey, record.getFinalKey(), record.getContentType());
            for (MediaDerivativeProcessor.DerivativeFile file : processed.files()) {
                storage.uploadDerivative(
                        storage.derivativeKey(record.getFinalKey(), file.width(), file.format()),
                        file.contentType(),
                        Path.of(file.path())
                );
            }
            verifyAllDerivatives(record.getFinalKey());
            attach(record);
            if (!record.getPendingKey().equals(record.getFinalKey())) {
                try {
                    storage.deleteOriginal(record.getPendingKey());
                } catch (RuntimeException error) {
                    log.warn("Could not delete processed pending upload {}", record.getPendingKey(), error);
                }
            }
            record.setStatus(MediaUploadModels.Status.READY);
            record.setError(null);
            stateStore.save(record);
        } catch (RuntimeException | IOException error) {
            record.setStatus(MediaUploadModels.Status.FAILED);
            record.setError(truncate(rootMessage(error), 1500));
            stateStore.save(record);
            log.warn("Media processing failed for upload {}", record.getId(), error);
        } finally {
            deleteDirectory(workDirectory);
        }
    }

    protected void attach(MediaUploadRecord record) {
        String publicUrl = storage.publicOriginalUrl(record.getFinalKey());
        if (record.getTargetType() == MediaUploadModels.TargetType.PRODUCT) {
            if (catalogService.findProductImageByObjectKey(record.getFinalKey()).isEmpty()) {
                catalogService.addProductImage(
                        record.getEntityId(),
                        publicUrl,
                        record.getFinalKey(),
                        record.getPosition(),
                        record.getVariantId()
                );
            }
            return;
        }
        Category category = catalogService.getByCategoryId(record.getEntityId())
                .orElseThrow(() -> new IllegalArgumentException("Category not found: " + record.getEntityId()));
        if (record.getPreviousObjectKey() == null) {
            record.setPreviousObjectKey(storage.extractManagedObjectKey(category.getImageUrl()));
            stateStore.save(record);
        }
        if (!publicUrl.equals(category.getImageUrl())) {
            catalogService.updateCategoryImage(record.getEntityId(), publicUrl);
        }
        String previousKey = record.getPreviousObjectKey();
        if (StringUtils.hasText(previousKey) && !previousKey.equals(record.getFinalKey())) {
            try {
                storage.deleteOriginalAndDerivatives(previousKey);
            } catch (RuntimeException error) {
                log.warn("Could not delete replaced category image {}", previousKey, error);
            }
        }
    }

    public MediaUploadModels.UploadItemResponse toResponse(
            MediaUploadRecord record,
            MediaObjectStorageService.PresignedRequest singlePut
    ) {
        long partSize = record.getUploadMethod() == MediaUploadModels.UploadMethod.MULTIPART
                ? properties.getMultipartPartSize().toBytes()
                : 0;
        return new MediaUploadModels.UploadItemResponse(
                record.getId(),
                record.getBatchId(),
                record.getTargetType(),
                record.getEntityId(),
                record.getVariantId(),
                record.getPosition(),
                record.getFilename(),
                record.getContentType(),
                record.getExpectedSize(),
                record.getUploadMethod(),
                record.getStatus(),
                singlePut != null ? singlePut.url() : null,
                singlePut != null ? singlePut.headers() : Map.of(),
                partSize,
                totalParts(record),
                record.getCreatedAt(),
                record.getUpdatedAt(),
                record.getWidth(),
                record.getHeight(),
                record.getError()
        );
    }

    private void validateTarget(MediaUploadModels.TargetType targetType, UUID entityId) {
        if (targetType == MediaUploadModels.TargetType.PRODUCT) {
            catalogService.getProduct(entityId)
                    .orElseThrow(() -> new IllegalArgumentException("Product not found: " + entityId));
        } else {
            catalogService.getByCategoryId(entityId)
                    .orElseThrow(() -> new IllegalArgumentException("Category not found: " + entityId));
        }
    }

    private void validateVariant(MediaUploadModels.TargetType targetType, UUID entityId, UUID variantId) {
        if (variantId == null) {
            return;
        }
        if (targetType != MediaUploadModels.TargetType.PRODUCT) {
            throw new IllegalArgumentException("Category images cannot be assigned to a product variant");
        }
        catalogService.getVariant(entityId, variantId);
    }

    private void validateFile(MediaUploadModels.CreateFileRequest file) {
        String contentType = normalizeContentType(file.contentType());
        if (!ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new IllegalArgumentException("Only JPEG, PNG, and WebP images are supported");
        }
        if (file.size() > properties.getMaxFileSize().toBytes()) {
            throw new IllegalArgumentException("Image exceeds the "
                    + properties.getMaxFileSize().toMegabytes() + " MB limit");
        }
    }

    private void validateCompletedParts(
            MediaUploadRecord record,
            List<MediaUploadModels.CompletePart> parts
    ) {
        int totalParts = totalParts(record);
        if (parts.size() != totalParts) {
            throw new IllegalArgumentException("Multipart upload requires exactly " + totalParts + " completed parts");
        }
        List<Integer> actual = parts.stream()
                .map(MediaUploadModels.CompletePart::partNumber)
                .sorted()
                .toList();
        for (int index = 0; index < totalParts; index++) {
            if (actual.get(index) != index + 1) {
                throw new IllegalArgumentException("Multipart upload part list is incomplete");
            }
        }
    }

    private void verifyAllDerivatives(String objectKey) {
        List<String> missing = storage.derivativeKeys(objectKey).stream()
                .filter(key -> !storage.derivativeExists(key))
                .toList();
        if (!missing.isEmpty()) {
            throw new IllegalStateException("Optimized image set is incomplete: " + missing.getFirst());
        }
    }

    private int totalParts(MediaUploadRecord record) {
        if (record.getUploadMethod() != MediaUploadModels.UploadMethod.MULTIPART) {
            return 1;
        }
        long partSize = properties.getMultipartPartSize().toBytes();
        return Math.toIntExact((record.getExpectedSize() + partSize - 1) / partSize);
    }

    private long partLength(MediaUploadRecord record, int partNumber) {
        long partSize = properties.getMultipartPartSize().toBytes();
        long offset = (long) (partNumber - 1) * partSize;
        return Math.min(partSize, record.getExpectedSize() - offset);
    }

    private void requireStatus(MediaUploadRecord record, MediaUploadModels.Status status) {
        if (record.getStatus() != status) {
            throw new IllegalStateException("Upload is " + record.getStatus().name().toLowerCase(Locale.ROOT));
        }
    }

    private boolean isAttached(MediaUploadRecord record) {
        if (record.getTargetType() == MediaUploadModels.TargetType.PRODUCT) {
            return catalogService.findProductImageByObjectKey(record.getFinalKey()).isPresent();
        }
        return catalogService.getByCategoryId(record.getEntityId())
                .map(Category::getImageUrl)
                .map(storage::extractManagedObjectKey)
                .filter(record.getFinalKey()::equals)
                .isPresent();
    }

    private String normalizeContentType(String contentType) {
        String normalized = contentType == null ? "" : contentType.trim().toLowerCase(Locale.ROOT);
        return normalized.equals("image/jpg") ? "image/jpeg" : normalized;
    }

    private String extensionFor(String contentType) {
        return switch (contentType) {
            case "image/jpeg" -> "jpg";
            case "image/png" -> "png";
            case "image/webp" -> "webp";
            default -> throw new IllegalArgumentException("Unsupported image type");
        };
    }

    private String targetPrefix(MediaUploadModels.TargetType targetType) {
        return targetType == MediaUploadModels.TargetType.PRODUCT ? "products" : "categories";
    }

    private String normalizePrefix(String prefix) {
        return prefix.trim().replaceAll("^/+", "").replaceAll("/+$", "");
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return StringUtils.hasText(current.getMessage())
                ? current.getMessage()
                : current.getClass().getSimpleName();
    }

    private String truncate(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private void deleteDirectory(Path directory) {
        if (directory == null || !Files.exists(directory)) {
            return;
        }
        try (var paths = Files.walk(directory)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException error) {
                    log.debug("Could not delete media work path {}", path, error);
                }
            });
        } catch (IOException error) {
            log.debug("Could not clean media work directory {}", directory, error);
        }
    }
}
