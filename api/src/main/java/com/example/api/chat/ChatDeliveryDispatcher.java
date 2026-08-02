package com.example.api.chat;

import com.example.api.chat.TelegramClient.TelegramSendResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.util.List;

@Service
public class ChatDeliveryDispatcher {
    private static final Logger log = LoggerFactory.getLogger(ChatDeliveryDispatcher.class);

    private final ChatDeliveryOutboxRepository deliveryRepository;
    private final ChatProperties properties;
    private final TelegramClient telegramClient;
    private final ChatService chatService;

    public ChatDeliveryDispatcher(ChatDeliveryOutboxRepository deliveryRepository,
                                  ChatProperties properties,
                                  TelegramClient telegramClient,
                                  ChatService chatService) {
        this.deliveryRepository = deliveryRepository;
        this.properties = properties;
        this.telegramClient = telegramClient;
        this.chatService = chatService;
    }

    @Scheduled(fixedDelayString = "${chat.telegram.dispatcher-fixed-delay-ms:5000}")
    public void scheduledDispatch() {
        if (!properties.isEnabled() || !properties.getTelegram().isDispatcherEnabled()) {
            return;
        }
        dispatchDue();
    }

    public int dispatchDue() {
        if (!properties.isEnabled()) {
            return 0;
        }
        int maxAttempts = Math.max(1, properties.getMaxAttempts());
        List<ChatDeliveryOutbox> due = deliveryRepository.findDue(
                List.of(ChatDeliveryStatus.PENDING, ChatDeliveryStatus.FAILED),
                OffsetDateTime.now(),
                maxAttempts,
                PageRequest.of(0, Math.max(1, properties.getBatchSize()))
        );
        due.forEach(delivery -> dispatchOne(delivery, maxAttempts));
        return due.size();
    }

    private void dispatchOne(ChatDeliveryOutbox delivery, int maxAttempts) {
        try {
            TelegramSendResult result = telegramClient.sendManagerMessage(formatForTelegram(delivery));
            delivery.setStatus(ChatDeliveryStatus.SENT);
            delivery.setSentAt(OffsetDateTime.now());
            delivery.setTelegramMessageId(result.messageId());
            delivery.setLastError(null);
            deliveryRepository.save(delivery);
            if (result.messageId() != null) {
                chatService.recordTelegramDelivery(delivery, result.chatId(), result.messageId());
            }
        } catch (RuntimeException ex) {
            int attempts = delivery.getAttemptCount() + 1;
            delivery.setAttemptCount(attempts);
            delivery.setStatus(ChatDeliveryStatus.FAILED);
            delivery.setLastError(truncate(rootMessage(ex), 2000));
            if (attempts < maxAttempts) {
                delivery.setNextAttemptAt(OffsetDateTime.now().plus(properties.getRetryDelay()));
            }
            deliveryRepository.save(delivery);
            log.warn("Chat delivery failed for message {} attempt {}", delivery.getMessage().getId(), attempts, ex);
        }
    }

    private String formatForTelegram(ChatDeliveryOutbox delivery) {
        ChatConversation conversation = delivery.getConversation();
        ChatMessage message = delivery.getMessage();
        StringBuilder builder = new StringBuilder();
        builder.append("Новый чат #")
                .append(String.valueOf(conversation.getId()).substring(0, 8))
                .append('\n');
        appendLine(builder, "Имя", conversation.getCustomerName());
        appendLine(builder, "Контакт", conversation.getCustomerContact());
        appendLine(builder, "Email", conversation.getCustomerEmail());
        appendLine(builder, "Страница", conversation.getPageUrl());
        builder.append('\n')
                .append(message.getBody())
                .append("\n\nОтветьте реплаем на это сообщение. /close закроет чат.");
        return builder.toString();
    }

    private void appendLine(StringBuilder builder, String label, String value) {
        if (StringUtils.hasText(value)) {
            builder.append(label).append(": ").append(value.trim()).append('\n');
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
