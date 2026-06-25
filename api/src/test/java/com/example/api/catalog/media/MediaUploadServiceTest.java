package com.example.api.catalog.media;

import com.example.api.catalog.DirectusBridgeSecurity;
import com.example.catalog.domain.Product;
import com.example.catalog.service.CatalogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.util.unit.DataSize;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MediaUploadServiceTest {
    @Mock
    private CatalogService catalogService;
    @Mock
    private MediaUploadStateStore stateStore;
    @Mock
    private MediaObjectStorageService storage;
    @Mock
    private MediaDerivativeProcessor derivativeProcessor;
    @Mock
    private MediaUploadFeatureFlag featureFlag;

    private MediaUploadProperties properties;
    private MediaUploadService service;
    private UUID productId;

    @BeforeEach
    void setUp() {
        properties = new MediaUploadProperties();
        properties.setMaxFileSize(DataSize.ofMegabytes(100));
        properties.setSinglePutThreshold(DataSize.ofMegabytes(16));
        properties.setMultipartPartSize(DataSize.ofMegabytes(8));
        properties.setDerivativeBucket("derivatives");
        service = new MediaUploadService(
                catalogService,
                properties,
                stateStore,
                storage,
                derivativeProcessor,
                featureFlag
        );
        productId = UUID.randomUUID();
        lenient().when(featureFlag.isEnabled()).thenReturn(true);
        lenient().when(catalogService.getProduct(productId)).thenReturn(Optional.of(new Product("Product", "", "product")));
        lenient().when(catalogService.getProductImages(productId)).thenReturn(List.of());
    }

    @Test
    void createsSinglePutForSixteenMiBFile() {
        when(storage.presignSinglePut(any(), any(), anyLong()))
                .thenReturn(new MediaObjectStorageService.PresignedRequest("https://signed", Map.of("content-type", "image/jpeg")));

        MediaUploadModels.CreateBatchResponse response = service.createBatch(
                request(16L * 1024 * 1024, "image/jpeg"),
                principal()
        );

        assertThat(response.items()).hasSize(1);
        assertThat(response.items().getFirst().uploadMethod()).isEqualTo(MediaUploadModels.UploadMethod.SINGLE);
        assertThat(response.items().getFirst().putUrl()).isEqualTo("https://signed");
        ArgumentCaptor<MediaUploadRecord> record = ArgumentCaptor.forClass(MediaUploadRecord.class);
        verify(stateStore).create(record.capture());
        assertThat(record.getValue().getPendingKey()).startsWith("media-upload-pending/");
        assertThat(record.getValue().getFinalKey()).startsWith("products/" + productId + "/");
    }

    @Test
    void createsEightMiBMultipartPartsAboveThreshold() {
        when(storage.createMultipartUpload(any(), any())).thenReturn("storage-upload");

        MediaUploadModels.CreateBatchResponse response = service.createBatch(
                request(17L * 1024 * 1024, "image/png"),
                principal()
        );

        MediaUploadModels.UploadItemResponse item = response.items().getFirst();
        assertThat(item.uploadMethod()).isEqualTo(MediaUploadModels.UploadMethod.MULTIPART);
        assertThat(item.partSize()).isEqualTo(8L * 1024 * 1024);
        assertThat(item.totalParts()).isEqualTo(3);
        assertThat(item.putUrl()).isNull();
    }

    @Test
    void acceptsExactOneHundredMiBLimitAndRejectsOneByteMore() {
        when(storage.createMultipartUpload(any(), any())).thenReturn("storage-upload");
        assertThat(service.createBatch(request(100L * 1024 * 1024, "image/webp"), principal()).items())
                .hasSize(1);

        assertThatThrownBy(() -> service.createBatch(
                request(100L * 1024 * 1024 + 1, "image/webp"),
                principal()
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("100 MB");
    }

    @Test
    void rejectsCreationWhileFeatureIsDisabled() {
        when(featureFlag.isEnabled()).thenReturn(false);

        assertThatThrownBy(() -> service.createBatch(request(1024, "image/jpeg"), principal()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("disabled");
        verify(storage, never()).presignSinglePut(any(), any(), anyLong());
    }

    @Test
    void completionQueuesOnlyAfterExactObjectSizeIsPresent() {
        MediaUploadRecord record = record(MediaUploadModels.UploadMethod.SINGLE, 1234);
        when(stateStore.require(record.getId())).thenReturn(record);
        when(storage.headOriginal(record.getPendingKey()))
                .thenReturn(HeadObjectResponse.builder().contentLength(1234L).build());

        MediaUploadModels.UploadItemResponse response = service.complete(record.getId(), null);

        assertThat(response.status()).isEqualTo(MediaUploadModels.Status.QUEUED);
        verify(stateStore).enqueue(record.getId());
    }

    @Test
    void duplicateCompletionDoesNotQueueAgain() {
        MediaUploadRecord record = record(MediaUploadModels.UploadMethod.SINGLE, 1234);
        record.setStatus(MediaUploadModels.Status.QUEUED);
        when(stateStore.require(record.getId())).thenReturn(record);

        assertThat(service.complete(record.getId(), null).status()).isEqualTo(MediaUploadModels.Status.QUEUED);
        verify(stateStore, never()).enqueue(any());
    }

    @Test
    void multipartCompletionResumesAfterStorageCompletedBeforeStateWasSaved() {
        MediaUploadRecord record = record(MediaUploadModels.UploadMethod.MULTIPART, 17L * 1024 * 1024);
        record.setStorageUploadId("storage-upload");
        when(stateStore.require(record.getId())).thenReturn(record);
        when(storage.originalExists(record.getPendingKey())).thenReturn(true);
        when(storage.headOriginal(record.getPendingKey()))
                .thenReturn(HeadObjectResponse.builder().contentLength(record.getExpectedSize()).build());

        MediaUploadModels.CompleteUploadRequest request = new MediaUploadModels.CompleteUploadRequest(List.of(
                new MediaUploadModels.CompletePart(1, "\"etag-1\""),
                new MediaUploadModels.CompletePart(2, "\"etag-2\""),
                new MediaUploadModels.CompletePart(3, "\"etag-3\"")
        ));

        assertThat(service.complete(record.getId(), request).status()).isEqualTo(MediaUploadModels.Status.QUEUED);
        verify(storage, never()).completeMultipart(any(), any(), any());
        verify(stateStore).enqueue(record.getId());
    }

    @Test
    void activeProcessingCannotBeAborted() {
        MediaUploadRecord record = record(MediaUploadModels.UploadMethod.MULTIPART, 17L * 1024 * 1024);
        record.setStatus(MediaUploadModels.Status.PROCESSING);
        when(stateStore.require(record.getId())).thenReturn(record);

        assertThatThrownBy(() -> service.abort(record.getId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("being optimized");
        verify(storage, never()).deleteOriginal(any());
    }

    private MediaUploadModels.CreateBatchRequest request(long size, String contentType) {
        return new MediaUploadModels.CreateBatchRequest(
                MediaUploadModels.TargetType.PRODUCT,
                productId,
                List.of(new MediaUploadModels.CreateFileRequest("photo.jpg", contentType, size, null, null))
        );
    }

    private MediaUploadRecord record(MediaUploadModels.UploadMethod method, long size) {
        MediaUploadRecord record = new MediaUploadRecord();
        record.setId(UUID.randomUUID());
        record.setBatchId(UUID.randomUUID());
        record.setTargetType(MediaUploadModels.TargetType.PRODUCT);
        record.setEntityId(productId);
        record.setFilename("photo.jpg");
        record.setContentType("image/jpeg");
        record.setExpectedSize(size);
        record.setUploadMethod(method);
        record.setStatus(MediaUploadModels.Status.UPLOADING);
        record.setPendingKey("media-upload-pending/test.jpg");
        record.setFinalKey("products/" + productId + "/test.jpg");
        record.setCreatedAt(java.time.OffsetDateTime.now());
        record.setUpdatedAt(java.time.OffsetDateTime.now());
        return record;
    }

    private DirectusBridgeSecurity.DirectusBridgePrincipal principal() {
        return new DirectusBridgeSecurity.DirectusBridgePrincipal(
                "user-id",
                "editor@example.test",
                null,
                "catalogue-role",
                "catalogue-role"
        );
    }
}
