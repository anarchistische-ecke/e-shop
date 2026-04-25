package com.example.api.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationServiceTest {

    @Test
    void invalidRecipientIsRecordedAsSkippedAndDoesNotThrow() {
        NotificationOutboxRepository repository = mock(NotificationOutboxRepository.class);
        when(repository.existsByEventKey("event-1")).thenReturn(false);
        when(repository.saveAndFlush(any(NotificationOutbox.class))).thenAnswer(invocation -> invocation.getArgument(0));
        NotificationService service = new NotificationService(repository, new ObjectMapper());

        boolean enqueued = service.enqueueOnce(NotificationType.ORDER_PAID, "event-1", "bad-address", Map.of("orderId", "1"));

        assertThat(enqueued).isFalse();
        ArgumentCaptor<NotificationOutbox> captor = ArgumentCaptor.forClass(NotificationOutbox.class);
        verify(repository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(NotificationOutboxStatus.SKIPPED);
        assertThat(captor.getValue().getLastError()).contains("recipient");
    }

    @Test
    void duplicateEventKeyDoesNotCreateAnotherOutboxRow() {
        NotificationOutboxRepository repository = mock(NotificationOutboxRepository.class);
        when(repository.existsByEventKey("event-1")).thenReturn(true);
        NotificationService service = new NotificationService(repository, new ObjectMapper());

        boolean enqueued = service.enqueueOnce(NotificationType.ORDER_PAID, "event-1", "buyer@example.test", Map.of());

        assertThat(enqueued).isFalse();
        verify(repository, never()).saveAndFlush(any());
    }
}
