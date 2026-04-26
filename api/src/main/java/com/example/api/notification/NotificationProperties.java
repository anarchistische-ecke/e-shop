package com.example.api.notification;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
@ConfigurationProperties(prefix = "notifications")
public class NotificationProperties {
    private boolean enabled = true;
    private int maxAttempts = 3;
    private Duration retryDelay = Duration.ofMinutes(5);
    private int batchSize = 20;
    private Map<String, String> trackingUrlTemplates = new LinkedHashMap<>();
    private Dispatcher dispatcher = new Dispatcher();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(int maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    public Duration getRetryDelay() {
        return retryDelay;
    }

    public void setRetryDelay(Duration retryDelay) {
        this.retryDelay = retryDelay;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public Map<String, String> getTrackingUrlTemplates() {
        return trackingUrlTemplates;
    }

    public void setTrackingUrlTemplates(Map<String, String> trackingUrlTemplates) {
        this.trackingUrlTemplates = trackingUrlTemplates;
    }

    public Dispatcher getDispatcher() {
        return dispatcher;
    }

    public void setDispatcher(Dispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    public static class Dispatcher {
        private boolean enabled = true;
        private long fixedDelayMs = 30000;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public long getFixedDelayMs() {
            return fixedDelayMs;
        }

        public void setFixedDelayMs(long fixedDelayMs) {
            this.fixedDelayMs = fixedDelayMs;
        }
    }
}
