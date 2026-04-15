package com.example.api.content;

import com.example.api.catalog.CatalogController;
import com.example.api.catalog.CataloguePresentationService;
import com.example.api.catalog.CatalogueResponseFactory;
import com.example.api.config.GlobalExceptionHandler;
import com.example.catalog.domain.Product;
import com.example.catalog.service.CatalogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class CatalogueContentControllerTest {

    @Mock
    private CataloguePresentationService presentationService;

    @Mock
    private CatalogueResponseFactory responseFactory;

    @Mock
    private CatalogService catalogService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        DirectusContentProperties properties = new DirectusContentProperties();
        properties.setResponseCacheMaxAge(Duration.ofSeconds(60));
        properties.setResponseCacheStaleWhileRevalidate(Duration.ofMinutes(5));
        properties.setResponseCacheStaleIfError(Duration.ofHours(1));

        mockMvc = MockMvcBuilders.standaloneSetup(new CatalogueContentController(
                        presentationService,
                        responseFactory,
                        catalogService,
                        properties
                ))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void getCollection_returnsPublicCacheHeaders() throws Exception {
        when(presentationService.getPublishedStorefrontCollection("spring-drop")).thenReturn(
                new CatalogueContentModels.StorefrontCollection(
                        "spring-drop",
                        "Spring Drop",
                        "Curated picks",
                        "hybrid",
                        "category",
                        "bedroom",
                        null,
                        6,
                        "newest",
                        "Spring Drop",
                        "Curated picks",
                        null,
                        null,
                        null,
                        null,
                        OffsetDateTime.parse("2026-04-15T09:00:00Z"),
                        List.of()
                )
        );

        mockMvc.perform(get("/content/collections/spring-drop"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "public, max-age=60, stale-while-revalidate=300, stale-if-error=3600"))
                .andExpect(jsonPath("$.key").value("spring-drop"))
                .andExpect(jsonPath("$.mode").value("hybrid"));

        verify(presentationService).getPublishedStorefrontCollection("spring-drop");
    }

    @Test
    void previewCollection_disablesBrowserCaching() throws Exception {
        when(presentationService.getPreviewStorefrontCollection("spring-drop")).thenReturn(
                new CatalogueContentModels.StorefrontCollection(
                        "spring-drop",
                        "Preview Drop",
                        null,
                        "manual",
                        null,
                        null,
                        null,
                        3,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        List.of()
                )
        );

        mockMvc.perform(get("/content/preview/collections/spring-drop"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "private, no-store, max-age=0"))
                .andExpect(jsonPath("$.title").value("Preview Drop"));

        verify(presentationService).getPreviewStorefrontCollection("spring-drop");
    }

    @Test
    void previewProduct_returnsPreviewPayload() throws Exception {
        Product product = new Product("Linen Duvet", "Soft linen", "linen-duvet");
        product.setId(UUID.randomUUID());

        CatalogController.ProductResponse response = new CatalogController.ProductResponse();
        response.setId(product.getId());
        response.setSlug("linen-duvet");
        response.setName("Linen Duvet");
        response.setPresentation(new CatalogueContentModels.CataloguePresentation(
                new CatalogueContentModels.PresentationSource("preview_overlay", "linen-duvet", "draft", null, true, true),
                null,
                null,
                null,
                null,
                null,
                "Draft title",
                null,
                null,
                null,
                List.of(),
                List.of()
        ));

        when(catalogService.getProductBySlug("linen-duvet")).thenReturn(Optional.of(product));
        when(presentationService.buildPreviewProductPresentation(product)).thenReturn(
                new com.example.api.catalog.CataloguePresentationModels.OverlayMergeResult(response.getPresentation(), false)
        );
        when(responseFactory.toProductResponse(product, response.getPresentation())).thenReturn(response);

        mockMvc.perform(get("/content/preview/catalogue/products/linen-duvet"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "private, no-store, max-age=0"))
                .andExpect(jsonPath("$.slug").value("linen-duvet"))
                .andExpect(jsonPath("$.presentation.marketingTitle").value("Draft title"));

        verify(catalogService).getProductBySlug("linen-duvet");
    }
}
