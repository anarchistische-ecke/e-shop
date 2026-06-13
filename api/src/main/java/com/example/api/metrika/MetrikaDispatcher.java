package com.example.api.metrika;

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
public class MetrikaDispatcher {
    private static final Logger log = LoggerFactory.getLogger(MetrikaDispatcher.class);

    private final MetrikaOutboxRepository outboxRepository;
    private final MetrikaClient metrikaClient;
    private final MetrikaProperties properties;
    private final Counter sentCounter;
    private final Counter failedCounter;
    private final Counter skippedCounter;

    public MetrikaDispatcher(MetrikaOutboxRepository outboxRepository,
                             MetrikaClient metrikaClient,
                             MetrikaProperties properties,
                             MeterRegistry meterRegistry) {
        this.outboxRepository = outboxRepository;
        this.metrikaClient = metrikaClient;
        this.properties = properties;
        this.sentCounter = Counter.builder("metrika.outbox.sent").register(meterRegistry);
        this.failedCounter = Counter.builder("metrika.outbox.failed").register(meterRegistry);
        this.skippedCounter = Counter.builder("metrika.outbox.skipped").register(meterRegistry);
    }

    @Scheduled(fixedDelayString = "${metrika.dispatcher.fixed-delay-ms:60000}")
    public void scheduledDispatch() {
        if (!properties.getDispatcher().isEnabled()) {
            return;
        }
        dispatchDue();
    }

    public int dispatchDue() {
        if (!properties.isEnabled() || !properties.getOfflineImport().isEnabled()) {
            return 0;
        }
        int maxAttempts = Math.max(1, properties.getMaxAttempts());
        List<MetrikaOutbox> due = outboxRepository.findDue(
                List.of(MetrikaOutboxStatus.PENDING, MetrikaOutboxStatus.FAILED),
                OffsetDateTime.now(),
                maxAttempts,
                PageRequest.of(0, Math.max(1, properties.getBatchSize()))
        );
        int dispatched = 0;
        for (MetrikaOutbox outbox : due) {
            dispatchOne(outbox, maxAttempts);
            dispatched++;
        }
        return dispatched;
    }

    private void dispatchOne(MetrikaOutbox outbox, int maxAttempts) {
        if (!StringUtils.hasText(outbox.getPayload())) {
            outbox.setStatus(MetrikaOutboxStatus.SKIPPED);
            outbox.setLastError("Missing conversion payload");
            outboxRepository.save(outbox);
            skippedCounter.increment();
            return;
        }

        try {
            metrikaClient.uploadOfflineConversion(outbox.getPayload());
            outbox.setStatus(MetrikaOutboxStatus.SENT);
            outbox.setSentAt(OffsetDateTime.now());
            outbox.setLastError(null);
            outboxRepository.save(outbox);
            sentCounter.increment();
        } catch (RuntimeException ex) {
            int attempts = outbox.getAttemptCount() + 1;
            outbox.setAttemptCount(attempts);
            outbox.setStatus(MetrikaOutboxStatus.FAILED);
            outbox.setLastError(truncate(rootMessage(ex), 2000));
            if (attempts < maxAttempts) {
                outbox.setNextAttemptAt(OffsetDateTime.now().plus(properties.getRetryDelay()));
            }
            outboxRepository.save(outbox);
            failedCounter.increment();
            log.warn("Metrika dispatch failed for event key {} attempt {}", outbox.getEventKey(), attempts, ex);
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
