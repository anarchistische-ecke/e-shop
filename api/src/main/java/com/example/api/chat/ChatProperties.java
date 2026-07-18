package com.example.api.chat;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@ConfigurationProperties(prefix = "chat")
public class ChatProperties {
    private boolean enabled = true;
    private int maxMessageLength = 2000;
    private int maxAttempts = 3;
    private int batchSize = 20;
    private Duration retryDelay = Duration.ofSeconds(30);
    private Telegram telegram = new Telegram();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getMaxMessageLength() {
        return maxMessageLength;
    }

    public void setMaxMessageLength(int maxMessageLength) {
        this.maxMessageLength = maxMessageLength;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(int maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public Duration getRetryDelay() {
        return retryDelay;
    }

    public void setRetryDelay(Duration retryDelay) {
        this.retryDelay = retryDelay;
    }

    public Telegram getTelegram() {
        return telegram;
    }

    public void setTelegram(Telegram telegram) {
        this.telegram = telegram;
    }

    public static class Telegram {
        private String botToken = "";
        private String managerChatId = "";
        private String webhookSecret = "";
        private String apiBaseUrl = "https://api.telegram.org";
        private boolean dispatcherEnabled = true;
        private long dispatcherFixedDelayMs = 5000L;

        public String getBotToken() {
            return botToken;
        }

        public void setBotToken(String botToken) {
            this.botToken = botToken;
        }

        public String getManagerChatId() {
            return managerChatId;
        }

        public void setManagerChatId(String managerChatId) {
            this.managerChatId = managerChatId;
        }

        public String getWebhookSecret() {
            return webhookSecret;
        }

        public void setWebhookSecret(String webhookSecret) {
            this.webhookSecret = webhookSecret;
        }

        public String getApiBaseUrl() {
            return apiBaseUrl;
        }

        public void setApiBaseUrl(String apiBaseUrl) {
            this.apiBaseUrl = apiBaseUrl;
        }

        public boolean isDispatcherEnabled() {
            return dispatcherEnabled;
        }

        public void setDispatcherEnabled(boolean dispatcherEnabled) {
            this.dispatcherEnabled = dispatcherEnabled;
        }

        public long getDispatcherFixedDelayMs() {
            return dispatcherFixedDelayMs;
        }

        public void setDispatcherFixedDelayMs(long dispatcherFixedDelayMs) {
            this.dispatcherFixedDelayMs = dispatcherFixedDelayMs;
        }
    }
}
