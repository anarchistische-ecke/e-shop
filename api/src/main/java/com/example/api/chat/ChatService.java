package com.example.api.chat;

import com.example.api.chat.ChatModels.ConversationResponse;
import com.example.api.chat.ChatModels.CreateConversationRequest;
import com.example.api.chat.ChatModels.MessageListResponse;
import com.example.api.chat.ChatModels.MessageResponse;
import com.example.api.chat.ChatModels.SendMessageRequest;
import com.example.api.chat.ChatModels.TelegramMessage;
import com.example.api.chat.ChatModels.TelegramUpdate;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class ChatService {
    private static final SecureRandom RANDOM = new SecureRandom();

    private final ChatConversationRepository conversationRepository;
    private final ChatMessageRepository messageRepository;
    private final ChatDeliveryOutboxRepository deliveryRepository;
    private final ChatTelegramMessageMapRepository telegramMessageMapRepository;
    private final ChatTelegramUpdateRepository telegramUpdateRepository;
    private final ChatProperties properties;
    private final TelegramClient telegramClient;

    public ChatService(ChatConversationRepository conversationRepository,
                       ChatMessageRepository messageRepository,
                       ChatDeliveryOutboxRepository deliveryRepository,
                       ChatTelegramMessageMapRepository telegramMessageMapRepository,
                       ChatTelegramUpdateRepository telegramUpdateRepository,
                       ChatProperties properties,
                       TelegramClient telegramClient) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.deliveryRepository = deliveryRepository;
        this.telegramMessageMapRepository = telegramMessageMapRepository;
        this.telegramUpdateRepository = telegramUpdateRepository;
        this.properties = properties;
        this.telegramClient = telegramClient;
    }

    @Transactional
    public ConversationResponse createConversation(CreateConversationRequest request, Jwt jwt, String userAgent) {
        requireEnabled();
        String token = generateToken();
        ChatConversation conversation = new ChatConversation();
        conversation.setCustomerTokenHash(hashToken(token));
        conversation.setCustomerName(normalize(request.customerName(), 160));
        conversation.setCustomerContact(normalize(request.customerContact(), 240));
        conversation.setPageUrl(normalize(request.pageUrl(), 1000));
        conversation.setUserAgent(normalize(userAgent, 1000));
        if (jwt != null) {
            conversation.setCustomerSubject(normalize(jwt.getSubject(), 255));
            conversation.setCustomerEmail(normalize(jwt.getClaimAsString("email"), 255));
        }
        conversation = conversationRepository.save(conversation);

        String firstMessage = normalizeMessage(request.message(), false);
        if (StringUtils.hasText(firstMessage)) {
            addCustomerMessage(conversation, firstMessage);
        }

        return new ConversationResponse(
                conversation.getId(),
                token,
                conversation.getStatus(),
                toResponses(messageRepository.findByConversation_IdOrderByCreatedAtAscIdAsc(conversation.getId()))
        );
    }

    @Transactional(readOnly = true)
    public MessageListResponse listMessages(UUID conversationId, String token, String after) {
        ChatConversation conversation = requireConversation(conversationId, token);
        OffsetDateTime afterTime = parseAfter(after);
        List<ChatMessage> messages = afterTime == null
                ? messageRepository.findByConversation_IdOrderByCreatedAtAscIdAsc(conversation.getId())
                : messageRepository.findByConversation_IdAndCreatedAtAfterOrderByCreatedAtAscIdAsc(
                        conversation.getId(),
                        afterTime
                );
        return new MessageListResponse(
                conversation.getId(),
                conversation.getStatus(),
                toResponses(messages)
        );
    }

    @Transactional
    public MessageListResponse sendCustomerMessage(UUID conversationId, String token, SendMessageRequest request) {
        ChatConversation conversation = requireConversation(conversationId, token);
        if (conversation.getStatus() == ChatConversationStatus.CLOSED) {
            throw new IllegalStateException("Chat conversation is closed");
        }
        ChatMessage message = addCustomerMessage(conversation, normalizeMessage(request.message(), true));
        return new MessageListResponse(conversation.getId(), conversation.getStatus(), List.of(toResponse(message)));
    }

    @Transactional
    public void recordTelegramDelivery(ChatDeliveryOutbox delivery, long telegramChatId, long telegramMessageId) {
        ChatTelegramMessageMap mapping = new ChatTelegramMessageMap();
        mapping.setConversation(delivery.getConversation());
        mapping.setMessage(delivery.getMessage());
        mapping.setTelegramChatId(telegramChatId);
        mapping.setTelegramMessageId(telegramMessageId);
        telegramMessageMapRepository.save(mapping);
    }

    @Transactional
    public void handleTelegramUpdate(TelegramUpdate update) {
        requireEnabled();
        if (update == null || update.update_id() == null) {
            return;
        }
        if (telegramUpdateRepository.existsByUpdateId(update.update_id())) {
            return;
        }
        ChatTelegramUpdate processed = new ChatTelegramUpdate();
        processed.setUpdateId(update.update_id());
        processed.setProcessedAt(OffsetDateTime.now());
        try {
            telegramUpdateRepository.saveAndFlush(processed);
        } catch (DataIntegrityViolationException ex) {
            return;
        }

        TelegramMessage message = update.message();
        if (message == null || message.chat() == null || message.message_id() == null) {
            return;
        }
        String text = normalizeMessage(message.text(), false);
        if (!StringUtils.hasText(text)) {
            return;
        }
        TelegramMessage reply = message.reply_to_message();
        if (reply == null || reply.message_id() == null) {
            telegramClient.sendText(message.chat().id(), "Ответьте реплаем на сообщение клиента, чтобы отправить ответ в чат.");
            return;
        }

        ChatTelegramMessageMap mapping = telegramMessageMapRepository
                .findByTelegramChatIdAndTelegramMessageId(message.chat().id(), reply.message_id())
                .orElse(null);
        if (mapping == null) {
            telegramClient.sendText(message.chat().id(), "Не удалось найти связанный чат. Ответьте реплаем на последнее сообщение клиента.");
            return;
        }

        ChatConversation conversation = mapping.getConversation();
        if ("/close".equals(text.trim().toLowerCase(Locale.ROOT))) {
            closeConversation(conversation);
            telegramClient.sendText(message.chat().id(), "Чат закрыт.");
            return;
        }
        if (conversation.getStatus() == ChatConversationStatus.CLOSED) {
            telegramClient.sendText(message.chat().id(), "Этот чат уже закрыт.");
            return;
        }

        ChatMessage managerMessage = new ChatMessage();
        managerMessage.setConversation(conversation);
        managerMessage.setSender(ChatMessageSender.MANAGER);
        managerMessage.setSenderLabel(managerLabel(message));
        managerMessage.setBody(text);
        managerMessage.setSource("TELEGRAM");
        managerMessage = messageRepository.save(managerMessage);
        touchConversation(conversation, managerMessage.getCreatedAt());

        ChatTelegramMessageMap replyMapping = new ChatTelegramMessageMap();
        replyMapping.setConversation(conversation);
        replyMapping.setMessage(managerMessage);
        replyMapping.setTelegramChatId(message.chat().id());
        replyMapping.setTelegramMessageId(message.message_id());
        telegramMessageMapRepository.save(replyMapping);
    }

    private ChatMessage addCustomerMessage(ChatConversation conversation, String text) {
        ChatMessage message = new ChatMessage();
        message.setConversation(conversation);
        message.setSender(ChatMessageSender.CUSTOMER);
        message.setSenderLabel(customerLabel(conversation));
        message.setBody(text);
        message.setSource("WEB");
        message = messageRepository.save(message);
        touchConversation(conversation, message.getCreatedAt());

        ChatDeliveryOutbox delivery = new ChatDeliveryOutbox();
        delivery.setConversation(conversation);
        delivery.setMessage(message);
        delivery.setNextAttemptAt(OffsetDateTime.now());
        deliveryRepository.save(delivery);
        return message;
    }

    private void closeConversation(ChatConversation conversation) {
        conversation.setStatus(ChatConversationStatus.CLOSED);
        conversation.setClosedAt(OffsetDateTime.now());
        conversationRepository.save(conversation);
    }

    private void touchConversation(ChatConversation conversation, OffsetDateTime messageTime) {
        conversation.setLastMessageAt(messageTime != null ? messageTime : OffsetDateTime.now());
        conversationRepository.save(conversation);
    }

    private ChatConversation requireConversation(UUID conversationId, String token) {
        requireEnabled();
        if (conversationId == null || !StringUtils.hasText(token)) {
            throw new IllegalArgumentException("Chat token is required");
        }
        return conversationRepository.findByIdAndCustomerTokenHash(conversationId, hashToken(token))
                .orElseThrow(() -> new IllegalArgumentException("Chat conversation was not found"));
    }

    private void requireEnabled() {
        if (!properties.isEnabled()) {
            throw new IllegalStateException("Chat is disabled");
        }
    }

    private OffsetDateTime parseAfter(String after) {
        if (!StringUtils.hasText(after)) {
            return null;
        }
        try {
            return OffsetDateTime.parse(after.trim());
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException("Invalid after timestamp");
        }
    }

    private String normalizeMessage(String value, boolean required) {
        String normalized = normalize(value, Math.max(1, properties.getMaxMessageLength()));
        if (required && !StringUtils.hasText(normalized)) {
            throw new IllegalArgumentException("Message is required");
        }
        return normalized;
    }

    private String normalize(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= maxLength ? trimmed : trimmed.substring(0, maxLength);
    }

    private String generateToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.trim().getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to hash chat token", ex);
        }
    }

    private String customerLabel(ChatConversation conversation) {
        if (StringUtils.hasText(conversation.getCustomerName())) {
            return conversation.getCustomerName();
        }
        if (StringUtils.hasText(conversation.getCustomerEmail())) {
            return conversation.getCustomerEmail();
        }
        return "Покупатель";
    }

    private String managerLabel(TelegramMessage message) {
        if (message.from() == null) {
            return "Менеджер";
        }
        String firstName = message.from().first_name();
        String lastName = message.from().last_name();
        String fullName = String.join(" ",
                StringUtils.hasText(firstName) ? firstName : "",
                StringUtils.hasText(lastName) ? lastName : ""
        ).trim();
        if (StringUtils.hasText(fullName)) {
            return fullName;
        }
        if (StringUtils.hasText(message.from().username())) {
            return "@" + message.from().username();
        }
        return "Менеджер";
    }

    private List<MessageResponse> toResponses(List<ChatMessage> messages) {
        return messages.stream().map(this::toResponse).toList();
    }

    private MessageResponse toResponse(ChatMessage message) {
        return new MessageResponse(
                message.getId(),
                message.getSender(),
                message.getSenderLabel(),
                message.getBody(),
                message.getCreatedAt()
        );
    }
}
