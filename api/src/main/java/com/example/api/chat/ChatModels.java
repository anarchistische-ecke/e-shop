package com.example.api.chat;

import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class ChatModels {
    private ChatModels() {
    }

    public record CreateConversationRequest(
            @Size(max = 160) String customerName,
            @Size(max = 240) String customerContact,
            @Size(max = 2000) String message,
            @Size(max = 1000) String pageUrl
    ) {
    }

    public record SendMessageRequest(
            @Size(max = 2000) String message
    ) {
    }

    public record ConversationResponse(
            UUID conversationId,
            String conversationToken,
            ChatConversationStatus status,
            List<MessageResponse> messages
    ) {
    }

    public record MessageListResponse(
            UUID conversationId,
            ChatConversationStatus status,
            List<MessageResponse> messages
    ) {
    }

    public record MessageResponse(
            UUID id,
            ChatMessageSender sender,
            String senderLabel,
            String body,
            OffsetDateTime createdAt
    ) {
    }

    public record TelegramUpdate(
            Long update_id,
            TelegramMessage message
    ) {
    }

    public record TelegramMessage(
            Long message_id,
            TelegramChat chat,
            TelegramUser from,
            String text,
            TelegramMessage reply_to_message
    ) {
    }

    public record TelegramChat(
            Long id,
            String type,
            String title
    ) {
    }

    public record TelegramUser(
            Long id,
            String first_name,
            String last_name,
            String username,
            Boolean is_bot
    ) {
    }
}
