package com.example.api.chat;

import com.example.api.chat.ChatModels.ConversationResponse;
import com.example.api.chat.ChatModels.MessageListResponse;
import com.example.api.chat.ChatModels.MessageResponse;
import com.example.api.config.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.core.MethodParameter;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ChatControllerTest {
    @Mock
    private ChatService chatService;

    private ChatProperties properties;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        properties = new ChatProperties();
        properties.getTelegram().setWebhookSecret("secret");
        mockMvc = MockMvcBuilders.standaloneSetup(new ChatController(chatService, properties))
                .setCustomArgumentResolvers(optionalJwtResolver())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private HandlerMethodArgumentResolver optionalJwtResolver() {
        return new HandlerMethodArgumentResolver() {
            @Override
            public boolean supportsParameter(MethodParameter parameter) {
                return Jwt.class.equals(parameter.getParameterType());
            }

            @Override
            public Object resolveArgument(MethodParameter parameter,
                                          ModelAndViewContainer mavContainer,
                                          NativeWebRequest webRequest,
                                          WebDataBinderFactory binderFactory) {
                return null;
            }
        };
    }

    @Test
    void createConversationReturnsTokenAndMessages() throws Exception {
        UUID conversationId = UUID.randomUUID();
        when(chatService.createConversation(any(), any(), eq("Mozilla"))).thenReturn(new ConversationResponse(
                conversationId,
                "token",
                ChatConversationStatus.OPEN,
                List.of(new MessageResponse(UUID.randomUUID(), ChatMessageSender.CUSTOMER, "Анна", "Здравствуйте", OffsetDateTime.now()))
        ));

        mockMvc.perform(post("/chat/conversations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("User-Agent", "Mozilla")
                        .content("""
                                {"customerName":"Анна","customerContact":"+7 900","message":"Здравствуйте","pageUrl":"https://site.test"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.conversationId").value(conversationId.toString()))
                .andExpect(jsonPath("$.conversationToken").value("token"))
                .andExpect(jsonPath("$.messages[0].body").value("Здравствуйте"));
    }

    @Test
    void listMessagesReturnsValidationErrorForInvalidToken() throws Exception {
        UUID conversationId = UUID.randomUUID();
        when(chatService.listMessages(conversationId, "bad-token", null))
                .thenThrow(new IllegalArgumentException("Chat conversation was not found"));

        mockMvc.perform(get("/chat/conversations/{id}/messages", conversationId)
                        .header(ChatController.CHAT_TOKEN_HEADER, "bad-token"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void sendMessagePassesTokenToService() throws Exception {
        UUID conversationId = UUID.randomUUID();
        when(chatService.sendCustomerMessage(eq(conversationId), eq("token"), any())).thenReturn(new MessageListResponse(
                conversationId,
                ChatConversationStatus.OPEN,
                List.of(new MessageResponse(UUID.randomUUID(), ChatMessageSender.CUSTOMER, "Покупатель", "Есть вопрос", OffsetDateTime.now()))
        ));

        mockMvc.perform(post("/chat/conversations/{id}/messages", conversationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(ChatController.CHAT_TOKEN_HEADER, "token")
                        .content("{\"message\":\"Есть вопрос\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.messages[0].body").value("Есть вопрос"));

        verify(chatService).sendCustomerMessage(eq(conversationId), eq("token"), any());
    }

    @Test
    void telegramWebhookRejectsInvalidSecret() throws Exception {
        mockMvc.perform(post("/chat/telegram/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(ChatController.TELEGRAM_SECRET_HEADER, "wrong")
                        .content("{\"update_id\":1}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void telegramWebhookAcceptsValidSecret() throws Exception {
        mockMvc.perform(post("/chat/telegram/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(ChatController.TELEGRAM_SECRET_HEADER, "secret")
                        .content("{\"update_id\":1}"))
                .andExpect(status().isNoContent());

        verify(chatService).handleTelegramUpdate(any());
    }
}
