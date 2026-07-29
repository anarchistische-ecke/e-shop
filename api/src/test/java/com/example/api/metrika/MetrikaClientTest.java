package com.example.api.metrika;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;

class MetrikaClientTest {
    private MockWebServer server;
    private MetrikaClient client;

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();

        MetrikaProperties properties = new MetrikaProperties();
        properties.setEnabled(true);
        properties.setCounterId("109831177");
        properties.setOauthToken(" oauth-token ");
        properties.getOfflineImport().setEnabled(true);
        properties.getOfflineImport().setUrl(
                server.url("/") + "management/v1/counter/{counterId}/offline_conversions/upload"
        );

        client = new MetrikaClient(new RestTemplate(), properties);
    }

    @AfterEach
    void tearDown() throws Exception {
        server.shutdown();
    }

    @Test
    void uploadOfflineConversionUsesYandexOauthAuthorizationScheme() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200).setBody("{}"));

        client.uploadOfflineConversion("ClientId,Target,DateTime\n123,purchase_paid,1710000000");

        var request = server.takeRequest();
        assertThat(request.getMethod()).isEqualTo("POST");
        assertThat(request.getPath())
                .isEqualTo("/management/v1/counter/109831177/offline_conversions/upload");
        assertThat(request.getHeader("Authorization")).isEqualTo("OAuth oauth-token");
        assertThat(request.getHeader("Content-Type")).startsWith("multipart/form-data;");
        assertThat(request.getBody().readUtf8())
                .contains("metrika-offline-conversions.csv", "purchase_paid");
    }
}
