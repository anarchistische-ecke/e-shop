package com.example.api.content;

import com.example.api.config.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.OffsetDateTime;
import java.time.Duration;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ContentControllerTest {

    @Mock
    private ContentService contentService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        DirectusContentProperties properties = new DirectusContentProperties();
        properties.setResponseCacheMaxAge(Duration.ofSeconds(60));
        properties.setResponseCacheStaleWhileRevalidate(Duration.ofMinutes(5));
        properties.setResponseCacheStaleIfError(Duration.ofHours(1));

        mockMvc = MockMvcBuilders.standaloneSetup(new ContentController(contentService, properties))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void getNavigation_returnsCmsPayload() throws Exception {
        when(contentService.getNavigation("footer")).thenReturn(List.of(
                new ContentModels.NavigationGroup(
                        "footer-service",
                        "Service",
                        "footer",
                        "Service links",
                        10,
                        List.of(new ContentModels.NavigationItem("Delivery", "/info/delivery", "internal_path", false, "all", 10))
                )
        ));

        mockMvc.perform(get("/content/navigation").param("placement", "footer"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "public, max-age=60, stale-while-revalidate=300, stale-if-error=3600"))
                .andExpect(jsonPath("$[0].key").value("footer-service"))
                .andExpect(jsonPath("$[0].items[0].label").value("Delivery"));

        verify(contentService).getNavigation("footer");
    }

    @Test
    void getPageBySlug_returnsNotFoundErrorBodyWhenContentIsMissing() throws Exception {
        when(contentService.getPageBySlug("missing"))
                .thenThrow(new ContentNotFoundException("Published page not found in Directus for slug: missing"));

        mockMvc.perform(get("/content/pages/missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CONTENT_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Published page not found in Directus for slug: missing"));

        verify(contentService).getPageBySlug("missing");
    }

    @Test
    void getSiteSettings_serializesMediaAsset() throws Exception {
        when(contentService.getSiteSettings()).thenReturn(new ContentModels.SiteSettings(
                "Cozyhome",
                "Brand description",
                "+7 (999) 111-22-33",
                "support@example.com",
                "Cozyhome",
                "Cozyhome LLC",
                "1234567890",
                "0987654321",
                "Moscow",
                2015,
                "Cozyhome",
                "Fallback SEO description",
                new ContentModels.MediaAsset("og-1", "http://cms.test/assets/og-1", 1200, 630, "Share image", "image/jpeg"),
                OffsetDateTime.parse("2026-04-12T11:30:00Z")
        ));

        mockMvc.perform(get("/content/site-settings"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "public, max-age=60, stale-while-revalidate=300, stale-if-error=3600"))
                .andExpect(jsonPath("$.siteName").value("Cozyhome"))
                .andExpect(jsonPath("$.defaultOgImage.url").value("http://cms.test/assets/og-1"))
                .andExpect(jsonPath("$.defaultOgImage.alt").value("Share image"));

        verify(contentService).getSiteSettings();
    }

    @Test
    void previewEndpoints_disableBrowserCaching() throws Exception {
        when(contentService.getPreviewSiteSettings()).thenReturn(new ContentModels.SiteSettings(
                "Cozyhome",
                "Brand description",
                "+7 (999) 111-22-33",
                "support@example.com",
                "Cozyhome",
                "Cozyhome LLC",
                "1234567890",
                "0987654321",
                "Moscow",
                2015,
                "Cozyhome",
                "Fallback SEO description",
                null,
                OffsetDateTime.parse("2026-04-12T11:30:00Z")
        ));

        mockMvc.perform(get("/content/preview/site-settings"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "private, no-store, max-age=0"));

        verify(contentService).getPreviewSiteSettings();
    }
}
