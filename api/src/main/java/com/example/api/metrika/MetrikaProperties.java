package com.example.api.metrika;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;

@Component
@ConfigurationProperties(prefix = "metrika")
public class MetrikaProperties {
    private static final Logger log = LoggerFactory.getLogger(MetrikaProperties.class);

    private boolean enabled = false;
    private String counterId = "";
    private String oauthToken = "";
    private int batchSize = 20;
    private int maxAttempts = 6;
    private Duration retryDelay = Duration.ofMinutes(10);
    private OfflineImport offlineImport = new OfflineImport();
    private Dispatcher dispatcher = new Dispatcher();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getCounterId() {
        return counterId;
    }

    public void setCounterId(String counterId) {
        this.counterId = counterId;
    }

    public String getOauthToken() {
        return oauthToken;
    }

    public void setOauthToken(String oauthToken) {
        this.oauthToken = oauthToken;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
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

    public OfflineImport getOfflineImport() {
        return offlineImport;
    }

    public void setOfflineImport(OfflineImport offlineImport) {
        this.offlineImport = offlineImport;
    }

    public Dispatcher getDispatcher() {
        return dispatcher;
    }

    public void setDispatcher(Dispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    @PostConstruct
    public void validateConfiguration() {
        if (!enabled || !offlineImport.isEnabled()) {
            if (enabled) {
                log.warn("Yandex Metrica is enabled, but server-side offline conversion import is disabled");
            }
            return;
        }
        if (!StringUtils.hasText(counterId)) {
            throw new IllegalStateException(
                    "YANDEX_METRIKA_COUNTER_ID is required when YANDEX_METRIKA_OFFLINE_IMPORT_ENABLED=true"
            );
        }
        if (!StringUtils.hasText(oauthToken)) {
            throw new IllegalStateException(
                    "YANDEX_METRIKA_OAUTH_TOKEN is required when YANDEX_METRIKA_OFFLINE_IMPORT_ENABLED=true"
            );
        }
    }

    public static class OfflineImport {
        private boolean enabled = false;
        private String url = "";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }
    }

    public static class Dispatcher {
        private boolean enabled = true;
        private long fixedDelayMs = 60000;

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
