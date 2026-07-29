package com.example.api.chat;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;

class TelegramClientTest {
    private MockWebServer server;
    private TelegramClient client;

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
        ChatProperties properties = new ChatProperties();
        properties.getTelegram().setBotToken("bot-token");
        properties.getTelegram().setManagerChatId("-100");
        properties.getTelegram().setApiBaseUrl(server.url("/").toString());
        client = new TelegramClient(new RestTemplate(), properties);
    }

    @AfterEach
    void tearDown() throws Exception {
        server.shutdown();
    }

    @Test
    void sendManagerMessagePostsTelegramSendMessagePayload() throws Exception {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"ok\":true,\"result\":{\"message_id\":123}}"));

        TelegramClient.TelegramSendResult result = client.sendManagerMessage("Здравствуйте");

        assertThat(result.chatId()).isEqualTo(-100L);
        assertThat(result.messageId()).isEqualTo(123L);
        var request = server.takeRequest();
        assertThat(request.getPath()).isEqualTo("/botbot-token/sendMessage");
        assertThat(request.getBody().readUtf8()).contains("\"chat_id\":-100", "\"text\":\"Здравствуйте\"");
    }
}
