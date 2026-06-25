package com.example.api.catalog.media;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

@Service
public class MediaUploadDispatcher {
    private static final Logger log = LoggerFactory.getLogger(MediaUploadDispatcher.class);

    private final MediaUploadProperties properties;
    private final MediaUploadStateStore stateStore;
    private final MediaUploadService uploadService;
    private final MediaProcessingLock processingLock;
    private Instant lastRecovery = Instant.EPOCH;

    public MediaUploadDispatcher(
            MediaUploadProperties properties,
            MediaUploadStateStore stateStore,
            MediaUploadService uploadService,
            MediaProcessingLock processingLock
    ) {
        this.properties = properties;
        this.stateStore = stateStore;
        this.uploadService = uploadService;
        this.processingLock = processingLock;
    }

    @Scheduled(fixedDelayString = "${catalogue.media.upload.worker-fixed-delay-ms:2000}")
    public void dispatch() {
        if (!properties.isProcessorEnabled()) {
            return;
        }
        recoverPeriodically();
        try (MediaProcessingLock.Lease lease = processingLock.tryAcquire()) {
            if (lease == null) {
                return;
            }
            uploadService.recoverInterruptedProcessing();
            MediaUploadStateStore.QueuedUpload queued = stateStore.peekQueue();
            if (queued == null) {
                return;
            }
            try {
                uploadService.process(queued.uploadId());
                stateStore.acknowledgeQueue(queued.recordId());
            } catch (RuntimeException error) {
                log.warn("Unexpected media dispatcher failure for {}", queued.uploadId(), error);
            }
        }
    }

    private void recoverPeriodically() {
        Instant now = Instant.now();
        if (Duration.between(lastRecovery, now).compareTo(Duration.ofMinutes(1)) < 0) {
            return;
        }
        lastRecovery = now;
        try {
            uploadService.expireStaleUploads();
        } catch (RuntimeException error) {
            log.warn("Media upload recovery failed", error);
        }
    }
}
