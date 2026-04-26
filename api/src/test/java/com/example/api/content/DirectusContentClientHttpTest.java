package com.example.api.content;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DirectusContentClientHttpTest {

    private MockWebServer server;
    private DirectusContentClient client;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();

        DirectusContentProperties properties = new DirectusContentProperties();
        properties.setBaseUrl(server.url("/").toString());
        properties.setStaticToken("directus-test-token");
        properties.setConnectTimeout(Duration.ofSeconds(2));
        properties.setReadTimeout(Duration.ofSeconds(2));

        client = new DirectusContentClient(
                RestClient.builder(),
                properties,
                new CmsObservabilityService(new SimpleMeterRegistry(), properties)
        );
    }

    @AfterEach
    void tearDown() throws IOException {
        server.close();
    }

    @Test
    void fetchNavigationGroups_addsPublishedFiltersAndAuthHeader() throws Exception {
        enqueueJson("""
                {
                  "data": [
                    {
                      "id": 2,
                      "key": "footer_service",
                      "title": "Service",
                      "placement": "footer",
                      "description": "Service links",
                      "sort": 20
                    }
                  ]
                }
                """);

        List<DirectusContentClient.DirectusNavigationGroup> groups = client.fetchNavigationGroups(" Footer ", ContentAccessMode.PUBLISHED);
        RecordedRequest request = takeRequest();

        assertThat(groups).hasSize(1);
        assertThat(groups.getFirst().key()).isEqualTo("footer_service");
        assertThat(request.getHeader("Authorization")).isEqualTo("Bearer directus-test-token");
        assertThat(request.getRequestUrl().encodedPath()).isEqualTo("/items/navigation");
        assertThat(request.getRequestUrl().queryParameter("filter[status][_eq]")).isEqualTo("published");
        assertThat(request.getRequestUrl().queryParameter("filter[placement][_eq]")).isEqualTo("footer");
        assertThat(request.getRequestUrl().queryParameter("sort")).isEqualTo("sort,key");
        assertThat(request.getRequestUrl().queryParameter("limit")).isEqualTo("-1");
    }

    @Test
    void fetchPageBySlug_previewUsesNonArchivedFilter() throws Exception {
        enqueueJson("""
                {
                  "data": [
                    {
                      "id": 10,
                      "slug": "delivery",
                      "path": "/info/delivery",
                      "title": "Delivery",
                      "template": "content",
                      "nav_label": "Delivery",
                      "summary": "Delivery summary",
                      "seo_title": "Delivery SEO",
                      "seo_description": "Delivery SEO description",
                      "seo_image": "seo-asset",
                      "published_at": "2026-04-12T10:00:00Z"
                    }
                  ]
                }
                """);

        DirectusContentClient.DirectusPage page = client.fetchPageBySlug("delivery", ContentAccessMode.PREVIEW);
        RecordedRequest request = takeRequest();

        assertThat(page.slug()).isEqualTo("delivery");
        assertThat(request.getRequestUrl().encodedPath()).isEqualTo("/items/page");
        assertThat(request.getRequestUrl().queryParameter("filter[slug][_eq]")).isEqualTo("delivery");
        assertThat(request.getRequestUrl().queryParameter("filter[status][_neq]")).isEqualTo("archived");
        assertThat(request.getRequestUrl().queryParameter("filter[status][_eq]")).isNull();
        assertThat(request.getRequestUrl().queryParameter("limit")).isEqualTo("1");
    }

    @Test
    void fetchSiteSettings_previewRejectsArchivedSingleton() {
        enqueueJson("""
                {
                  "data": {
                    "site_name": "Cozyhome",
                    "brand_description": "Brand",
                    "support_phone": "+7",
                    "support_email": "support@example.com",
                    "legal_entity_short": "Cozyhome",
                    "legal_entity_full": "Cozyhome LLC",
                    "legal_inn": "123",
                    "legal_ogrnip": "456",
                    "legal_address": "Moscow",
                    "copyright_start_year": 2015,
                    "default_seo_title_suffix": "Cozyhome",
                    "default_seo_description": "Desc",
                    "default_og_image": "og-1",
                    "status": "archived",
                    "published_at": "2026-04-12T10:00:00Z"
                  }
                }
                """);

        assertThatThrownBy(() -> client.fetchSiteSettings(ContentAccessMode.PREVIEW))
                .isInstanceOf(ContentNotFoundException.class)
                .hasMessage("Preview site settings not found in Directus");
    }

    @Test
    void fetchFiles_parsesMetadataAndSkipsIncompleteRows() throws Exception {
        enqueueJson("""
                {
                  "data": [
                    {
                      "id": "file-1",
                      "title": "Hero title",
                      "description": "Hero alt",
                      "width": 1600,
                      "height": "900",
                      "filename_download": "hero.jpg",
                      "type": "image/jpeg"
                    },
                    {
                      "title": "Ignored row"
                    }
                  ]
                }
                """);

        List<DirectusContentClient.DirectusFileAsset> files = client.fetchFiles(List.of(" file-1 ", "file-1", ""));
        RecordedRequest request = takeRequest();

        assertThat(files).hasSize(1);
        assertThat(files.getFirst().id()).isEqualTo("file-1");
        assertThat(files.getFirst().width()).isEqualTo(1600);
        assertThat(files.getFirst().height()).isEqualTo(900);
        assertThat(request.getRequestUrl().encodedPath()).isEqualTo("/files");
        assertThat(request.getRequestUrl().queryParameter("filter[id][_in]")).isEqualTo("file-1");
        assertThat(client.assetUrl(" file-1 ")).isEqualTo(server.url("/assets/file-1").toString());
    }

    @Test
    void assetUrl_prefersConfiguredPublicUrl() {
        DirectusContentProperties properties = new DirectusContentProperties();
        properties.setBaseUrl("http://directus-internal:8055");
        properties.setPublicUrl("https://cms.example.com/");
        properties.setConnectTimeout(Duration.ofSeconds(2));
        properties.setReadTimeout(Duration.ofSeconds(2));

        DirectusContentClient publicClient = new DirectusContentClient(
                RestClient.builder(),
                properties,
                new CmsObservabilityService(new SimpleMeterRegistry(), properties)
        );

        assertThat(publicClient.assetUrl(" file-1 ")).isEqualTo("https://cms.example.com/assets/file-1");
    }

    @Test
    void fetchPageBySlug_throwsNotFoundWhenDirectusReturnsNoItems() {
        enqueueJson("""
                {
                  "data": []
                }
                """);

        assertThatThrownBy(() -> client.fetchPageBySlug("missing", ContentAccessMode.PUBLISHED))
                .isInstanceOf(ContentNotFoundException.class)
                .hasMessage("Published page not found in Directus for slug: missing");
    }

    private void enqueueJson(String body) {
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(body));
    }

    private RecordedRequest takeRequest() throws InterruptedException {
        RecordedRequest request = server.takeRequest(1, TimeUnit.SECONDS);
        assertThat(request).isNotNull();
        return request;
    }
}
