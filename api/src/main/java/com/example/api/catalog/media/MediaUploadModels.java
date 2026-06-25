package com.example.api.catalog.media;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class MediaUploadModels {
    private MediaUploadModels() {
    }

    public enum TargetType {
        PRODUCT,
        CATEGORY
    }

    public enum UploadMethod {
        SINGLE,
        MULTIPART
    }

    public enum Status {
        UPLOADING,
        QUEUED,
        PROCESSING,
        READY,
        FAILED,
        ABORTED,
        EXPIRED
    }

    public record CreateBatchRequest(
            @NotNull TargetType targetType,
            @NotNull UUID entityId,
            @NotEmpty List<@Valid CreateFileRequest> files
    ) {
    }

    public record CreateFileRequest(
            @NotBlank String filename,
            @NotBlank String contentType,
            @Positive long size,
            UUID variantId,
            Integer position
    ) {
    }

    public record CreateBatchResponse(
            UUID batchId,
            boolean uploadsEnabled,
            long maxFileSize,
            long maxPixels,
            long singlePutThreshold,
            long multipartPartSize,
            List<UploadItemResponse> items
    ) {
    }

    public record UploadItemResponse(
            UUID id,
            UUID batchId,
            TargetType targetType,
            UUID entityId,
            UUID variantId,
            int position,
            String filename,
            String contentType,
            long size,
            UploadMethod uploadMethod,
            Status status,
            String putUrl,
            Map<String, String> putHeaders,
            long partSize,
            int totalParts,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt,
            Integer width,
            Integer height,
            String error
    ) {
    }

    public record SignPartsRequest(@NotEmpty List<@Positive Integer> partNumbers) {
    }

    public record SignedPart(int partNumber, String url, Map<String, String> headers) {
    }

    public record SignPartsResponse(UUID uploadId, List<SignedPart> parts) {
    }

    public record RenewSingleResponse(UUID uploadId, String url, Map<String, String> headers) {
    }

    public record CompletePart(@Positive int partNumber, @NotBlank String eTag) {
    }

    public record CompleteUploadRequest(List<@Valid CompletePart> parts) {
    }

    public record UploadBatchStatus(UUID batchId, List<UploadItemResponse> items) {
    }

    public record FeatureFlagRequest(boolean enabled) {
    }

    public record FeatureFlagResponse(boolean enabled) {
    }
}
