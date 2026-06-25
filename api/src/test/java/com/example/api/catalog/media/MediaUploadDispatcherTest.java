package com.example.api.catalog.media;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MediaUploadDispatcherTest {
    @Mock
    private MediaUploadStateStore stateStore;
    @Mock
    private MediaUploadService uploadService;
    @Mock
    private MediaProcessingLock processingLock;
    @Mock
    private MediaProcessingLock.Lease lease;

    private MediaUploadProperties properties;
    private MediaUploadDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        properties = new MediaUploadProperties();
        properties.setProcessorEnabled(true);
        dispatcher = new MediaUploadDispatcher(properties, stateStore, uploadService, processingLock);
    }

    @Test
    void recoversInterruptedProcessingOnlyAfterOwningTheGlobalLock() {
        when(processingLock.tryAcquire()).thenReturn(lease);

        dispatcher.dispatch();

        verify(uploadService).recoverInterruptedProcessing();
        verify(stateStore).peekQueue();
        verify(lease).close();
    }

    @Test
    void competingSlotDoesNotRecoverJobsOwnedByTheActiveProcessor() {
        when(processingLock.tryAcquire()).thenReturn(null);

        dispatcher.dispatch();

        verify(uploadService, never()).recoverInterruptedProcessing();
        verify(stateStore, never()).peekQueue();
    }
}
