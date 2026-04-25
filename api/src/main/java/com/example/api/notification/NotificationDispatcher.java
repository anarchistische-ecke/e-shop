package com.example.api.notification;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.util.List;

@Service
public class NotificationDispatcher {
    private static final Logger log = LoggerFactory.getLogger(NotificationDispatcher.class);

    private final NotificationOutboxRepository outboxRepository;
    private final NotificationTemplateService templateService;
    private final EmailService emailService;
    private final NotificationProperties properties;
    private final Counter sentCounter;
    private final Counter failedCounter;
    private final Counter skippedCounter;

    public NotificationDispatcher(NotificationOutboxRepository outboxRepository,
                                  NotificationTemplateService templateService,
                                  EmailService emailService,
                                  NotificationProperties properties,
                                  MeterRegistry meterRegistry) {
        this.outboxRepository = outboxRepository;
        this.templateService = templateService;
        this.emailService = emailService;
        this.properties = properties;
        this.sentCounter = Counter.builder("notifications.sent").register(meterRegistry);
        this.failedCounter = Counter.builder("notifications.failed").register(meterRegistry);
        this.skippedCounter = Counter.builder("notifications.skipped").register(meterRegistry);
    }

    @Scheduled(fixedDelayString = "${notifications.dispatcher.fixed-delay-ms:30000}")
    public void scheduledDispatch() {
        if (!properties.getDispatcher().isEnabled()) {
            return;
        }
        dispatchDue();
    }

    public int dispatchDue() {
        if (!properties.isEnabled()) {
            return 0;
        }
        int maxAttempts = Math.max(1, properties.getMaxAttempts());
        List<NotificationOutbox> due = outboxRepository.findDue(
                List.of(NotificationOutboxStatus.PENDING, NotificationOutboxStatus.FAILED),
                OffsetDateTime.now(),
                maxAttempts,
                PageRequest.of(0, Math.max(1, properties.getBatchSize()))
        );
        int dispatched = 0;
        for (NotificationOutbox outbox : due) {
            dispatchOne(outbox, maxAttempts);
            dispatched++;
        }
        return dispatched;
    }

    private void dispatchOne(NotificationOutbox outbox, int maxAttempts) {
        if (!NotificationService.isValidEmail(outbox.getRecipient())) {
            outbox.setStatus(NotificationOutboxStatus.SKIPPED);
            outbox.setLastError("Missing or invalid recipient");
            outboxRepository.save(outbox);
            skippedCounter.increment();
            return;
        }
        try {
            RenderedNotification rendered = templateService.render(outbox.getType(), outbox.getPayloadJson());
            emailService.sendTransactionalEmail(
                    outbox.getRecipient(),
                    rendered.subject(),
                    rendered.htmlBody(),
                    rendered.textBody()
            );
            outbox.setStatus(NotificationOutboxStatus.SENT);
            outbox.setSentAt(OffsetDateTime.now());
            outbox.setLastError(null);
            outboxRepository.save(outbox);
            sentCounter.increment();
        } catch (RuntimeException ex) {
            int attempts = outbox.getAttemptCount() + 1;
            outbox.setAttemptCount(attempts);
            outbox.setStatus(NotificationOutboxStatus.FAILED);
            outbox.setLastError(truncate(rootMessage(ex), 2000));
            if (attempts < maxAttempts) {
                outbox.setNextAttemptAt(OffsetDateTime.now().plus(properties.getRetryDelay()));
            }
            outboxRepository.save(outbox);
            failedCounter.increment();
            log.warn("Notification dispatch failed for event key {} attempt {}", outbox.getEventKey(), attempts, ex);
        }
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return StringUtils.hasText(message) ? message : current.getClass().getSimpleName();
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
