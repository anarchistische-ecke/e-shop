package com.example.api.catalog;

import com.example.admin.service.AdminActivityService;
import com.example.api.config.GlobalExceptionHandler;
import com.example.api.content.CatalogueContentModels;
import com.example.api.content.CatalogueContentService;
import com.example.catalog.domain.Brand;
import com.example.catalog.domain.Category;
import com.example.catalog.domain.Product;
import com.example.catalog.service.CatalogService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class StorefrontOpsWorkspaceControllerTest {

    @Mock
    private CatalogService catalogService;

    @Mock
    private CatalogueContentService catalogueContentService;

    @Mock
    private StorefrontOpsWorkspaceFactory workspaceFactory;

    @Mock
    private DirectusBridgeSecurity bridgeSecurity;

    @Mock
    private AdminActivityService adminActivityService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        StorefrontOpsWorkspaceController controller = new StorefrontOpsWorkspaceController(
                catalogService,
                catalogueContentService,
                workspaceFactory,
                bridgeSecurity,
                adminActivityService,
                new ObjectMapper()
        );
        doNothing().when(bridgeSecurity).authorize(any());
        when(bridgeSecurity.principal(any())).thenReturn(new DirectusBridgeSecurity.DirectusBridgePrincipal(
                "user-1",
                "manager@example.com",
                "external-user-1",
                "catalogue-operator",
                "catalogue-operator"
        ));

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void listProducts_returnsWorkspacePayloadWithOverlayContext() throws Exception {
        Brand brand = new Brand("Aurora", "Brand", "aurora");
        Category category = new Category("Sofas", "Desc", "sofas", null);
        category.setFullPath("living/sofas");
        Product product = new Product("Cloud Sofa", "Soft", "cloud-sofa");
        product.setBrand(brand);
        product.setCategories(java.util.Set.of(category));

        CatalogueContentModels.CatalogueOverlay overlay = new CatalogueContentModels.CatalogueOverlay(
                17,
                "cloud-sofa",
                "product",
                "draft",
                OffsetDateTime.parse("2026-04-16T12:00:00Z"),
                null,
                null,
                null,
                "Хит",
                null,
                "Диван для весны",
                null,
                null,
                null,
                List.of(),
                List.of("spring-selection")
        );

        when(catalogService.getAllProducts()).thenReturn(List.of(product));
        when(catalogService.listAllInBrand()).thenReturn(List.of(brand));
        when(catalogService.listAllInCategory()).thenReturn(List.of(category));
        when(catalogueContentService.getPreviewProductOverlays(List.of("cloud-sofa"))).thenReturn(Map.of("cloud-sofa", overlay));
        when(workspaceFactory.toProductSummary(product, overlay)).thenReturn(new StorefrontOpsWorkspaceModels.ProductSummary(
                UUID.fromString("11111111-1111-4111-8111-111111111111"),
                "Cloud Sofa",
                "cloud-sofa",
                true,
                new StorefrontOpsWorkspaceModels.BrandOption(UUID.fromString("22222222-2222-4222-8222-222222222222"), "Aurora", "aurora"),
                List.of(new StorefrontOpsWorkspaceModels.CategoryOption(
                        UUID.fromString("33333333-3333-4333-8333-333333333333"),
                        "Sofas",
                        "sofas",
                        "living/sofas",
                        null,
                        1
                )),
                2,
                8,
                "https://cdn.example.com/sofa.jpg",
                OffsetDateTime.parse("2026-04-16T12:10:00Z"),
                new StorefrontOpsWorkspaceModels.OverlayWorkspaceInfo(
                        17,
                        "product_overlay",
                        "product_key",
                        "cloud-sofa",
                        "draft",
                        OffsetDateTime.parse("2026-04-16T12:00:00Z"),
                        true,
                        "Диван для весны",
                        "Хит",
                        List.of("spring-selection")
                )
        ));
        when(workspaceFactory.toBrandOptions(List.of(brand))).thenReturn(List.of(
                new StorefrontOpsWorkspaceModels.BrandOption(UUID.fromString("22222222-2222-4222-8222-222222222222"), "Aurora", "aurora")
        ));
        when(workspaceFactory.toCategoryOptions(List.of(category))).thenReturn(List.of(
                new StorefrontOpsWorkspaceModels.CategoryOption(
                        UUID.fromString("33333333-3333-4333-8333-333333333333"),
                        "Sofas",
                        "sofas",
                        "living/sofas",
                        null,
                        1
                )
        ));

        mockMvc.perform(get("/internal/directus/catalogue/workspace/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].name").value("Cloud Sofa"))
                .andExpect(jsonPath("$.items[0].overlay.collection").value("product_overlay"))
                .andExpect(jsonPath("$.items[0].overlay.linkedCollectionKeys[0]").value("spring-selection"))
                .andExpect(jsonPath("$.brandOptions[0].slug").value("aurora"))
                .andExpect(jsonPath("$.categoryOptions[0].fullPath").value("living/sofas"));
    }

    @Test
    void getCategory_returnsDetailPayloadWithOverlayState() throws Exception {
        Category category = new Category("Sofas", "Living room seating", "sofas", null);
        category.setFullPath("living/sofas");

        CatalogueContentModels.CatalogueOverlay overlay = new CatalogueContentModels.CatalogueOverlay(
                29,
                "sofas",
                "category",
                "published",
                OffsetDateTime.parse("2026-04-16T10:00:00Z"),
                null,
                null,
                null,
                null,
                null,
                "Лучшие диваны",
                null,
                null,
                null,
                null,
                List.of()
        );

        when(catalogService.getBySlug("sofas")).thenReturn(java.util.Optional.of(category));
        when(catalogService.listAllInCategory()).thenReturn(List.of(category));
        when(catalogueContentService.getPreviewCategoryOverlays(List.of("sofas"))).thenReturn(Map.of("sofas", overlay));
        when(workspaceFactory.toCategoryDetail(category, overlay)).thenReturn(new StorefrontOpsWorkspaceModels.CategoryDetail(
                UUID.fromString("33333333-3333-4333-8333-333333333333"),
                "Sofas",
                "sofas",
                "Living room seating",
                "https://cdn.example.com/category.jpg",
                null,
                1,
                0,
                true,
                "living/sofas",
                new StorefrontOpsWorkspaceModels.OverlayWorkspaceInfo(
                        29,
                        "category_overlay",
                        "category_key",
                        "sofas",
                        "published",
                        OffsetDateTime.parse("2026-04-16T10:00:00Z"),
                        true,
                        "Лучшие диваны",
                        null,
                        List.of()
                )
        ));
        when(workspaceFactory.toCategoryOptions(List.of(category))).thenReturn(List.of(
                new StorefrontOpsWorkspaceModels.CategoryOption(
                        UUID.fromString("33333333-3333-4333-8333-333333333333"),
                        "Sofas",
                        "sofas",
                        "living/sofas",
                        null,
                        1
                )
        ));

        mockMvc.perform(get("/internal/directus/catalogue/workspace/categories/sofas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.item.slug").value("sofas"))
                .andExpect(jsonPath("$.item.overlay.collection").value("category_overlay"))
                .andExpect(jsonPath("$.item.overlay.status").value("published"))
                .andExpect(jsonPath("$.parentOptions[0].name").value("Sofas"));
    }
}
