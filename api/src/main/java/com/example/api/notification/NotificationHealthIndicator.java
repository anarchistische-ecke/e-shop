package com.example.api.notification;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Component
public class NotificationHealthIndicator implements HealthIndicator {
    private final NotificationOutboxRepository outboxRepository;
    private final NotificationProperties properties;

    public NotificationHealthIndicator(NotificationOutboxRepository outboxRepository, NotificationProperties properties) {
        this.outboxRepository = outboxRepository;
        this.properties = properties;
    }

    @Override
    public Health health() {
        long pending = outboxRepository.countByStatus(NotificationOutboxStatus.PENDING);
        long failed = outboxRepository.countByStatus(NotificationOutboxStatus.FAILED);
        Health.Builder builder = properties.isEnabled() ? Health.up() : Health.unknown();
        return builder
                .withDetail("enabled", properties.isEnabled())
                .withDetail("pending", pending)
                .withDetail("failed", failed)
                .withDetail("skipped", outboxRepository.countByStatus(NotificationOutboxStatus.SKIPPED))
                .withDetail("sent", outboxRepository.countByStatus(NotificationOutboxStatus.SENT))
                .build();
    }
}
