package com.example.api.catalog.media;

import java.time.OffsetDateTime;
import java.util.UUID;

public class MediaUploadRecord {
    private UUID id;
    private UUID batchId;
    private MediaUploadModels.TargetType targetType;
    private UUID entityId;
    private UUID variantId;
    private int position;
    private String filename;
    private String contentType;
    private long expectedSize;
    private MediaUploadModels.UploadMethod uploadMethod;
    private MediaUploadModels.Status status;
    private String pendingKey;
    private String finalKey;
    private String previousObjectKey;
    private String storageUploadId;
    private String actor;
    private int attemptCount;
    private Integer width;
    private Integer height;
    private String error;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getBatchId() {
        return batchId;
    }

    public void setBatchId(UUID batchId) {
        this.batchId = batchId;
    }

    public MediaUploadModels.TargetType getTargetType() {
        return targetType;
    }

    public void setTargetType(MediaUploadModels.TargetType targetType) {
        this.targetType = targetType;
    }

    public UUID getEntityId() {
        return entityId;
    }

    public void setEntityId(UUID entityId) {
        this.entityId = entityId;
    }

    public UUID getVariantId() {
        return variantId;
    }

    public void setVariantId(UUID variantId) {
        this.variantId = variantId;
    }

    public int getPosition() {
        return position;
    }

    public void setPosition(int position) {
        this.position = position;
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public long getExpectedSize() {
        return expectedSize;
    }

    public void setExpectedSize(long expectedSize) {
        this.expectedSize = expectedSize;
    }

    public MediaUploadModels.UploadMethod getUploadMethod() {
        return uploadMethod;
    }

    public void setUploadMethod(MediaUploadModels.UploadMethod uploadMethod) {
        this.uploadMethod = uploadMethod;
    }

    public MediaUploadModels.Status getStatus() {
        return status;
    }

    public void setStatus(MediaUploadModels.Status status) {
        this.status = status;
    }

    public String getPendingKey() {
        return pendingKey;
    }

    public void setPendingKey(String pendingKey) {
        this.pendingKey = pendingKey;
    }

    public String getFinalKey() {
        return finalKey;
    }

    public void setFinalKey(String finalKey) {
        this.finalKey = finalKey;
    }

    public String getPreviousObjectKey() {
        return previousObjectKey;
    }

    public void setPreviousObjectKey(String previousObjectKey) {
        this.previousObjectKey = previousObjectKey;
    }

    public String getStorageUploadId() {
        return storageUploadId;
    }

    public void setStorageUploadId(String storageUploadId) {
        this.storageUploadId = storageUploadId;
    }

    public String getActor() {
        return actor;
    }

    public void setActor(String actor) {
        this.actor = actor;
    }

    public int getAttemptCount() {
        return attemptCount;
    }

    public void setAttemptCount(int attemptCount) {
        this.attemptCount = attemptCount;
    }

    public Integer getWidth() {
        return width;
    }

    public void setWidth(Integer width) {
        this.width = width;
    }

    public Integer getHeight() {
        return height;
    }

    public void setHeight(Integer height) {
        this.height = height;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
