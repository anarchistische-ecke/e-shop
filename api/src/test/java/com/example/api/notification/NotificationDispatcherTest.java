package com.example.api.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationDispatcherTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void smtpExceptionRecordsFailedAttemptWithoutBubbling() throws Exception {
        NotificationOutbox outbox = outbox("event-1", "buyer@example.test");
        NotificationOutboxRepository repository = mock(NotificationOutboxRepository.class);
        when(repository.findDue(anyCollection(), any(OffsetDateTime.class), eq(3), any(Pageable.class)))
                .thenReturn(List.of(outbox));
        EmailService emailService = mock(EmailService.class);
        doThrow(new IllegalStateException("smtp down")).when(emailService)
                .sendTransactionalEmail(any(), any(), any(), any());

        NotificationDispatcher dispatcher = dispatcher(repository, emailService, properties(3));

        assertThat(dispatcher.dispatchDue()).isEqualTo(1);
        assertThat(outbox.getStatus()).isEqualTo(NotificationOutboxStatus.FAILED);
        assertThat(outbox.getAttemptCount()).isEqualTo(1);
        assertThat(outbox.getLastError()).contains("smtp down");
        verify(repository).save(outbox);
    }

    @Test
    void failureAtMaxAttemptIsNotScheduledBeyondMaxAttempts() throws Exception {
        NotificationOutbox outbox = outbox("event-1", "buyer@example.test");
        outbox.setAttemptCount(2);
        NotificationOutboxRepository repository = mock(NotificationOutboxRepository.class);
        when(repository.findDue(anyCollection(), any(OffsetDateTime.class), eq(3), any(Pageable.class)))
                .thenReturn(List.of(outbox));
        EmailService emailService = mock(EmailService.class);
        doThrow(new IllegalStateException("smtp down")).when(emailService)
                .sendTransactionalEmail(any(), any(), any(), any());

        NotificationDispatcher dispatcher = dispatcher(repository, emailService, properties(3));

        dispatcher.dispatchDue();

        assertThat(outbox.getAttemptCount()).isEqualTo(3);
        assertThat(outbox.getStatus()).isEqualTo(NotificationOutboxStatus.FAILED);
    }

    @Test
    void invalidRecipientIsSkippedSafely() throws Exception {
        NotificationOutbox outbox = outbox("event-1", "");
        NotificationOutboxRepository repository = mock(NotificationOutboxRepository.class);
        when(repository.findDue(anyCollection(), any(OffsetDateTime.class), eq(3), any(Pageable.class)))
                .thenReturn(List.of(outbox));
        EmailService emailService = mock(EmailService.class);

        NotificationDispatcher dispatcher = dispatcher(repository, emailService, properties(3));

        dispatcher.dispatchDue();

        assertThat(outbox.getStatus()).isEqualTo(NotificationOutboxStatus.SKIPPED);
        verify(repository).save(outbox);
    }

    private NotificationDispatcher dispatcher(NotificationOutboxRepository repository,
                                              EmailService emailService,
                                              NotificationProperties properties) {
        return new NotificationDispatcher(
                repository,
                new NotificationTemplateService(objectMapper),
                emailService,
                properties,
                new SimpleMeterRegistry()
        );
    }

    private NotificationProperties properties(int maxAttempts) {
        NotificationProperties properties = new NotificationProperties();
        properties.setMaxAttempts(maxAttempts);
        properties.setRetryDelay(Duration.ofMinutes(5));
        return properties;
    }

    private NotificationOutbox outbox(String eventKey, String recipient) throws Exception {
        NotificationOutbox outbox = new NotificationOutbox();
        outbox.setEventKey(eventKey);
        outbox.setType(NotificationType.ORDER_PAID);
        outbox.setRecipient(recipient);
        outbox.setStatus(NotificationOutboxStatus.PENDING);
        outbox.setPayloadJson(objectMapper.writeValueAsString(Map.of(
                "orderId", "order-1",
                "status", "PAID",
                "amountFormatted", "100.00 RUB"
        )));
        outbox.setNextAttemptAt(OffsetDateTime.now().minusSeconds(1));
        return outbox;
    }
}
