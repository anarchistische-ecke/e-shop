package com.example.api.chat;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Service
public class TelegramClient {
    private final RestTemplate restTemplate;
    private final ChatProperties properties;

    public TelegramClient(RestTemplate restTemplate, ChatProperties properties) {
        this.restTemplate = restTemplate;
        this.properties = properties;
    }

    public TelegramSendResult sendManagerMessage(String text) {
        long chatId = managerChatId();
        return sendMessage(chatId, text, true);
    }

    public void sendText(Long chatId, String text) {
        if (chatId == null || !isConfigured() || !StringUtils.hasText(text)) {
            return;
        }
        try {
            sendMessage(chatId, text, false);
        } catch (RuntimeException ignored) {
        }
    }

    public boolean isConfigured() {
        return StringUtils.hasText(properties.getTelegram().getBotToken())
                && StringUtils.hasText(properties.getTelegram().getManagerChatId());
    }

    private TelegramSendResult sendMessage(long chatId, String text, boolean requireConfigured) {
        if (!isConfigured()) {
            if (requireConfigured) {
                throw new IllegalStateException("Telegram bot token or manager chat id is not configured");
            }
            return new TelegramSendResult(chatId, null);
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        Map<String, Object> body = Map.of(
                "chat_id", chatId,
                "text", text,
                "disable_web_page_preview", true
        );
        TelegramSendMessageResponse response = restTemplate
                .postForObject(apiUrl("sendMessage"), new HttpEntity<>(body, headers), TelegramSendMessageResponse.class);
        if (response == null || !Boolean.TRUE.equals(response.ok()) || response.result() == null) {
            throw new IllegalStateException("Telegram sendMessage returned an invalid response");
        }
        return new TelegramSendResult(chatId, response.result().message_id());
    }

    private long managerChatId() {
        try {
            return Long.parseLong(properties.getTelegram().getManagerChatId().trim());
        } catch (Exception ex) {
            throw new IllegalStateException("Telegram manager chat id must be numeric");
        }
    }

    private String apiUrl(String method) {
        String base = StringUtils.hasText(properties.getTelegram().getApiBaseUrl())
                ? properties.getTelegram().getApiBaseUrl().trim()
                : "https://api.telegram.org";
        return base.replaceAll("/$", "")
                + "/bot"
                + properties.getTelegram().getBotToken().trim()
                + "/"
                + method;
    }

    public record TelegramSendResult(long chatId, Long messageId) {
    }

    public record TelegramSendMessageResponse(Boolean ok, TelegramSentMessage result) {
    }

    public record TelegramSentMessage(Long message_id) {
    }
}
