package com.example.api.chat;

import com.example.api.chat.ChatModels.CreateConversationRequest;
import com.example.api.chat.ChatModels.SendMessageRequest;
import com.example.api.chat.ChatModels.TelegramChat;
import com.example.api.chat.ChatModels.TelegramMessage;
import com.example.api.chat.ChatModels.TelegramUpdate;
import com.example.api.chat.ChatModels.TelegramUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatServiceTest {
    @Mock
    private ChatConversationRepository conversationRepository;
    @Mock
    private ChatMessageRepository messageRepository;
    @Mock
    private ChatDeliveryOutboxRepository deliveryRepository;
    @Mock
    private ChatTelegramMessageMapRepository telegramMessageMapRepository;
    @Mock
    private ChatTelegramUpdateRepository telegramUpdateRepository;
    @Mock
    private TelegramClient telegramClient;

    private ChatService service;

    @BeforeEach
    void setUp() {
        ChatProperties properties = new ChatProperties();
        service = new ChatService(
                conversationRepository,
                messageRepository,
                deliveryRepository,
                telegramMessageMapRepository,
                telegramUpdateRepository,
                properties,
                telegramClient
        );
        lenient().when(conversationRepository.save(any(ChatConversation.class))).thenAnswer(invocation -> {
            ChatConversation conversation = invocation.getArgument(0);
            if (conversation.getId() == null) {
                conversation.setId(UUID.randomUUID());
            }
            if (conversation.getCreatedAt() == null) {
                conversation.setCreatedAt(OffsetDateTime.now());
                conversation.setUpdatedAt(conversation.getCreatedAt());
            }
            return conversation;
        });
        lenient().when(messageRepository.save(any(ChatMessage.class))).thenAnswer(invocation -> {
            ChatMessage message = invocation.getArgument(0);
            if (message.getId() == null) {
                message.setId(UUID.randomUUID());
            }
            if (message.getCreatedAt() == null) {
                message.setCreatedAt(OffsetDateTime.now());
                message.setUpdatedAt(message.getCreatedAt());
            }
            return message;
        });
    }

    @Test
    void createConversationStoresFirstMessageAndEnqueuesTelegramDelivery() {
        when(messageRepository.findByConversation_IdOrderByCreatedAtAscIdAsc(any())).thenReturn(List.of());

        var response = service.createConversation(
                new CreateConversationRequest("Анна", "+7 900 000-00-00", "Здравствуйте", "https://site.test/product"),
                null,
                "Mozilla"
        );

        assertThat(response.conversationId()).isNotNull();
        assertThat(response.conversationToken()).isNotBlank();
        ArgumentCaptor<ChatMessage> messageCaptor = ArgumentCaptor.forClass(ChatMessage.class);
        verify(messageRepository).save(messageCaptor.capture());
        assertThat(messageCaptor.getValue().getBody()).isEqualTo("Здравствуйте");
        assertThat(messageCaptor.getValue().getSender()).isEqualTo(ChatMessageSender.CUSTOMER);
        verify(deliveryRepository).save(any(ChatDeliveryOutbox.class));
    }

    @Test
    void sendCustomerMessageRequiresValidConversationToken() {
        UUID conversationId = UUID.randomUUID();
        when(conversationRepository.findByIdAndCustomerTokenHash(any(), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.sendCustomerMessage(conversationId, "bad-token", new SendMessageRequest("Hello")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void sendCustomerMessageRejectsClosedConversation() {
        ChatConversation conversation = conversation("hash");
        conversation.setStatus(ChatConversationStatus.CLOSED);
        when(conversationRepository.findByIdAndCustomerTokenHash(any(), any())).thenReturn(Optional.of(conversation));

        assertThatThrownBy(() -> service.sendCustomerMessage(conversation.getId(), "token", new SendMessageRequest("Hello")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("closed");
    }

    @Test
    void telegramWebhookStoresManagerReplyWhenReplyMapsToConversation() {
        ChatConversation conversation = conversation("hash");
        ChatTelegramMessageMap mapping = new ChatTelegramMessageMap();
        mapping.setConversation(conversation);
        when(telegramUpdateRepository.existsByUpdateId(100L)).thenReturn(false);
        when(telegramMessageMapRepository.findByTelegramChatIdAndTelegramMessageId(-10L, 55L))
                .thenReturn(Optional.of(mapping));

        service.handleTelegramUpdate(new TelegramUpdate(
                100L,
                new TelegramMessage(
                        77L,
                        new TelegramChat(-10L, "supergroup", "Managers"),
                        new TelegramUser(1L, "Иван", "Петров", "ivan", false),
                        "Ответ менеджера",
                        new TelegramMessage(55L, new TelegramChat(-10L, "supergroup", "Managers"), null, "original", null)
                )
        ));

        ArgumentCaptor<ChatMessage> messageCaptor = ArgumentCaptor.forClass(ChatMessage.class);
        verify(messageRepository).save(messageCaptor.capture());
        assertThat(messageCaptor.getValue().getSender()).isEqualTo(ChatMessageSender.MANAGER);
        assertThat(messageCaptor.getValue().getSenderLabel()).isEqualTo("Иван Петров");
        assertThat(messageCaptor.getValue().getBody()).isEqualTo("Ответ менеджера");
    }

    @Test
    void telegramWebhookIgnoresDuplicateUpdate() {
        when(telegramUpdateRepository.existsByUpdateId(100L)).thenReturn(true);

        service.handleTelegramUpdate(new TelegramUpdate(100L, null));

        verify(telegramUpdateRepository, never()).saveAndFlush(any());
        verify(messageRepository, never()).save(any());
    }

    @Test
    void telegramCloseCommandClosesConversation() {
        ChatConversation conversation = conversation("hash");
        ChatTelegramMessageMap mapping = new ChatTelegramMessageMap();
        mapping.setConversation(conversation);
        when(telegramUpdateRepository.existsByUpdateId(101L)).thenReturn(false);
        when(telegramMessageMapRepository.findByTelegramChatIdAndTelegramMessageId(-10L, 55L))
                .thenReturn(Optional.of(mapping));

        service.handleTelegramUpdate(new TelegramUpdate(
                101L,
                new TelegramMessage(
                        78L,
                        new TelegramChat(-10L, "supergroup", "Managers"),
                        new TelegramUser(1L, "Иван", null, "ivan", false),
                        "/close",
                        new TelegramMessage(55L, new TelegramChat(-10L, "supergroup", "Managers"), null, "original", null)
                )
        ));

        assertThat(conversation.getStatus()).isEqualTo(ChatConversationStatus.CLOSED);
        assertThat(conversation.getClosedAt()).isNotNull();
        verify(telegramClient).sendText(-10L, "Чат закрыт.");
    }

    @Test
    void telegramWebhookGuidesUnmatchedReplies() {
        when(telegramUpdateRepository.existsByUpdateId(102L)).thenReturn(false);
        when(telegramMessageMapRepository.findByTelegramChatIdAndTelegramMessageId(-10L, 55L))
                .thenReturn(Optional.empty());

        service.handleTelegramUpdate(new TelegramUpdate(
                102L,
                new TelegramMessage(
                        79L,
                        new TelegramChat(-10L, "supergroup", "Managers"),
                        new TelegramUser(1L, "Иван", null, "ivan", false),
                        "Ответ",
                        new TelegramMessage(55L, new TelegramChat(-10L, "supergroup", "Managers"), null, "original", null)
                )
        ));

        verify(telegramClient).sendText(-10L, "Не удалось найти связанный чат. Ответьте реплаем на последнее сообщение клиента.");
        verify(messageRepository, never()).save(any());
    }

    private ChatConversation conversation(String hash) {
        ChatConversation conversation = new ChatConversation();
        conversation.setId(UUID.randomUUID());
        conversation.setCustomerTokenHash(hash);
        conversation.setStatus(ChatConversationStatus.OPEN);
        conversation.setCreatedAt(OffsetDateTime.now());
        conversation.setUpdatedAt(conversation.getCreatedAt());
        return conversation;
    }
}
