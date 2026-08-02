package com.example.api.content;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DirectusPreviewClientTest {

    private MockWebServer server;
    private DirectusContentProperties properties;

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
        properties = new DirectusContentProperties();
        properties.setBaseUrl(server.url("/").toString());
        properties.setStaticToken("published-reader-token");
        properties.setPreviewStaticToken("preview-reader-token");
        properties.setConnectTimeout(Duration.ofSeconds(1));
        properties.setReadTimeout(Duration.ofSeconds(1));
    }

    @AfterEach
    void tearDown() throws Exception {
        server.shutdown();
    }

    @Test
    void readsSelectedVersionWithDedicatedPreviewCredential() throws Exception {
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""
                        {"data":{"id":"page-home","title":"Черновик"}}
                        """));
        DirectusPreviewClient client = client();

        assertThat(client.fetchItem("page", "page-home", "draft-version").get("title").asText())
                .isEqualTo("Черновик");

        RecordedRequest request = server.takeRequest(1, TimeUnit.SECONDS);
        assertThat(request).isNotNull();
        assertThat(request.getHeader("Authorization")).isEqualTo("Bearer preview-reader-token");
        assertThat(request.getRequestUrl().queryParameter("version")).isEqualTo("draft-version");
        assertThat(request.getRequestUrl().queryParameter("fields")).contains("sections.faqs.faq.*");
    }

    @Test
    void refusesPreviewWhenDedicatedCredentialIsAbsent() {
        properties.setPreviewStaticToken("");

        assertThatThrownBy(() -> client().fetchItem("campaign", "campaign-1", "draft-version"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DIRECTUS_PREVIEW_TOKEN");
        assertThat(server.getRequestCount()).isZero();
    }

    private DirectusPreviewClient client() {
        return new DirectusPreviewClient(
                RestClient.builder(),
                properties,
                new CmsObservabilityService(new SimpleMeterRegistry(), properties)
        );
    }
}
