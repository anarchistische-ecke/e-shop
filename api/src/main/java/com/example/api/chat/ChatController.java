package com.example.api.chat;

import com.example.api.chat.ChatModels.ConversationResponse;
import com.example.api.chat.ChatModels.CreateConversationRequest;
import com.example.api.chat.ChatModels.MessageListResponse;
import com.example.api.chat.ChatModels.SendMessageRequest;
import com.example.api.chat.ChatModels.TelegramUpdate;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/chat")
public class ChatController {
    public static final String CHAT_TOKEN_HEADER = "X-Chat-Token";
    public static final String TELEGRAM_SECRET_HEADER = "X-Telegram-Bot-Api-Secret-Token";

    private final ChatService chatService;
    private final ChatProperties properties;

    public ChatController(ChatService chatService, ChatProperties properties) {
        this.chatService = chatService;
        this.properties = properties;
    }

    @PostMapping("/conversations")
    public ResponseEntity<ConversationResponse> createConversation(@Valid @RequestBody CreateConversationRequest request,
                                                                   @AuthenticationPrincipal Jwt jwt,
                                                                   HttpServletRequest servletRequest) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(chatService.createConversation(request, jwt, servletRequest.getHeader("User-Agent")));
    }

    @GetMapping("/conversations/{conversationId}/messages")
    public ResponseEntity<MessageListResponse> listMessages(
            @PathVariable UUID conversationId,
            @RequestHeader(CHAT_TOKEN_HEADER) String token,
            @RequestParam(required = false) String after) {
        return ResponseEntity.ok(chatService.listMessages(conversationId, token, after));
    }

    @PostMapping("/conversations/{conversationId}/messages")
    public ResponseEntity<MessageListResponse> sendMessage(
            @PathVariable UUID conversationId,
            @RequestHeader(CHAT_TOKEN_HEADER) String token,
            @Valid @RequestBody SendMessageRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(chatService.sendCustomerMessage(conversationId, token, request));
    }

    @PostMapping("/telegram/webhook")
    public ResponseEntity<Void> telegramWebhook(@RequestHeader(value = TELEGRAM_SECRET_HEADER, required = false) String secret,
                                                @RequestBody TelegramUpdate update) {
        String expectedSecret = properties.getTelegram().getWebhookSecret();
        if (!StringUtils.hasText(expectedSecret) || !expectedSecret.equals(secret)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        chatService.handleTelegramUpdate(update);
        return ResponseEntity.noContent().build();
    }
}
