package com.example.api.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class NotificationService {
    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final NotificationOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public NotificationService(NotificationOutboxRepository outboxRepository, ObjectMapper objectMapper) {
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    public boolean enqueueOnce(NotificationType type, String eventKey, String recipient, Object payload) {
        return enqueueOnce(type, eventKey, null, null, recipient, payload);
    }

    public boolean enqueueOnce(NotificationType type,
                               String eventKey,
                               String aggregateType,
                               UUID aggregateId,
                               String recipient,
                               Object payload) {
        if (type == null || !StringUtils.hasText(eventKey)) {
            throw new IllegalArgumentException("Notification type and event key are required");
        }
        NotificationOutbox outbox = new NotificationOutbox();
        outbox.setType(type);
        outbox.setEventKey(eventKey.trim());
        outbox.setAggregateType(normalize(aggregateType));
        outbox.setAggregateId(aggregateId);
        outbox.setRecipient(normalize(recipient));
        outbox.setPayloadJson(writePayload(payload));
        outbox.setNextAttemptAt(OffsetDateTime.now());
        if (!isValidEmail(recipient)) {
            outbox.setStatus(NotificationOutboxStatus.SKIPPED);
            outbox.setLastError("Missing or invalid recipient");
        }

        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    enqueuePreparedSafely(outbox);
                }
            });
            return true;
        }
        return enqueuePreparedSafely(outbox);
    }

    private boolean enqueuePreparedSafely(NotificationOutbox outbox) {
        try {
            return enqueuePrepared(outbox);
        } catch (RuntimeException ex) {
            log.warn("Failed to enqueue notification {} for event key {}", outbox.getType(), outbox.getEventKey(), ex);
            return false;
        }
    }

    private boolean enqueuePrepared(NotificationOutbox outbox) {
        if (outboxRepository.existsByEventKey(outbox.getEventKey())) {
            return false;
        }
        try {
            outboxRepository.saveAndFlush(outbox);
            return outbox.getStatus() != NotificationOutboxStatus.SKIPPED;
        } catch (DataIntegrityViolationException ex) {
            return false;
        }
    }

    private String writePayload(Object payload) {
        Object resolved = payload != null ? payload : Map.of();
        try {
            return objectMapper.writeValueAsString(resolved);
        } catch (Exception ex) {
            log.warn("Failed to serialize notification payload", ex);
            return "{}";
        }
    }

    static boolean isValidEmail(String value) {
        return StringUtils.hasText(value) && EMAIL_PATTERN.matcher(value.trim()).matches();
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
