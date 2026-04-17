package com.example.api.catalog;

import com.example.api.content.CatalogueContentModels;
import com.example.api.content.CatalogueContentService;
import com.example.api.content.ContentModels;
import com.example.catalog.domain.Brand;
import com.example.catalog.domain.Product;
import com.example.catalog.domain.ProductVariant;
import com.example.catalog.repository.ProductVariantRepository;
import com.example.catalog.service.CatalogService;
import com.example.common.domain.Money;
import com.example.order.repository.OrderItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CataloguePresentationServiceTest {

    @Mock
    private CatalogueContentService catalogueContentService;

    @Mock
    private CatalogService catalogService;

    @Mock
    private OrderItemRepository orderItemRepository;

    @Mock
    private ProductVariantRepository productVariantRepository;

    private CataloguePresentationService service;

    @BeforeEach
    void setUp() {
        service = new CataloguePresentationService(
                catalogueContentService,
                catalogService,
                orderItemRepository,
                productVariantRepository
        );
    }

    @Test
    void buildPublishedProductPresentation_fallsBackToBackendWhenOverlayReadFails() {
        Product product = product("linen-duvet", "Linen Duvet", "Natural washed linen");
        Brand brand = new Brand("Cozy Linen", "Brand", "cozy-linen");
        product.setBrand(brand);

        when(catalogueContentService.getPublishedProductOverlays(List.of("linen-duvet")))
                .thenThrow(new IllegalStateException("Directus unavailable"));

        CataloguePresentationModels.OverlayMergeResult result = service.buildPublishedProductPresentation(product);

        assertThat(result.overlayReadFailed()).isTrue();
        assertThat(result.presentation().source().mode()).isEqualTo("backend_fallback");
        assertThat(result.presentation().marketingTitle()).isEqualTo("Linen Duvet");
        assertThat(result.presentation().marketingSubtitle()).isEqualTo("Cozy Linen");
        assertThat(result.presentation().hero()).isNotNull();
        assertThat(result.presentation().hero().title()).isEqualTo("Linen Duvet");
        assertThat(result.presentation().hero().body()).isEqualTo("Natural washed linen");
    }

    @Test
    void buildPublishedProductPresentation_prefersOverlayValuesAndKeepsFallbackHeroFields() {
        Product product = product("cotton-sheet", "Cotton Sheet", "Everyday cotton sheet");
        OffsetDateTime publishedAt = OffsetDateTime.parse("2026-04-15T08:30:00Z");

        ContentModels.PageSection overlayHero = new ContentModels.PageSection(
                "product-hero",
                "hero",
                5,
                "hero",
                "Collection",
                "Fresh Cotton",
                null,
                null,
                null,
                null,
                "Shop now",
                "/category/popular",
                null,
                null,
                "warm",
                "contained",
                publishedAt,
                List.of()
        );

        CatalogueContentModels.CatalogueOverlay overlay = new CatalogueContentModels.CatalogueOverlay(
                11,
                "cotton-sheet",
                "product",
                "published",
                publishedAt,
                "Fresh Cotton SEO",
                "Soft cotton bedding",
                null,
                "New drop",
                "Limited",
                "Fresh Cotton",
                null,
                "Editor intro",
                overlayHero,
                List.of(),
                List.of("spring-drop")
        );

        when(catalogueContentService.getPublishedProductOverlays(List.of("cotton-sheet")))
                .thenReturn(Map.of("cotton-sheet", overlay));

        CataloguePresentationModels.OverlayMergeResult result = service.buildPublishedProductPresentation(product);

        assertThat(result.overlayReadFailed()).isFalse();
        assertThat(result.presentation().source().mode()).isEqualTo("published_overlay");
        assertThat(result.presentation().marketingTitle()).isEqualTo("Fresh Cotton");
        assertThat(result.presentation().introBody()).isEqualTo("Editor intro");
        assertThat(result.presentation().badgeText()).isEqualTo("New drop");
        assertThat(result.presentation().hero().title()).isEqualTo("Fresh Cotton");
        assertThat(result.presentation().hero().body()).isEqualTo("Everyday cotton sheet");
        assertThat(result.presentation().linkedCollectionKeys()).containsExactly("spring-drop");
    }

    @Test
    void getPublishedStorefrontCollection_appliesHybridPinsExclusionsAndLimit() {
        Product pinned = product("pinned-set", "Pinned Set", "Pinned product");
        Product alpha = product("alpha-set", "Alpha Set", "Alpha product");
        Product beta = product("beta-set", "Beta Set", "Beta product");
        pinned.setVariants(Set.of(variant(pinned, "PIN-001", 129900)));
        alpha.setVariants(Set.of(variant(alpha, "ALPHA-001", 99900)));
        beta.setVariants(Set.of(variant(beta, "BETA-001", 89900)));

        CatalogueContentModels.StorefrontCollectionDefinition definition = new CatalogueContentModels.StorefrontCollectionDefinition(
                "spring-drop",
                "Spring Drop",
                "Curated picks",
                "hybrid",
                "category",
                "bedroom",
                null,
                2,
                "newest",
                "Spring Drop",
                "Curated picks",
                null,
                null,
                "Shop all",
                "/category/bedroom",
                "published",
                OffsetDateTime.parse("2026-04-15T09:00:00Z"),
                List.of(
                        new CatalogueContentModels.StorefrontCollectionRuleItem("product", "pinned-set", "pin", 10),
                        new CatalogueContentModels.StorefrontCollectionRuleItem("product", "beta-set", "exclude", 20)
                )
        );

        when(catalogueContentService.getStorefrontCollection("spring-drop")).thenReturn(definition);
        when(catalogueContentService.getPublishedProductOverlays(List.of("pinned-set", "alpha-set"))).thenReturn(Map.of());
        when(catalogService.getProducts("bedroom", null)).thenReturn(List.of(alpha, beta));
        when(catalogService.getProductsBySlugs(List.of("pinned-set", "alpha-set"))).thenReturn(List.of(pinned, alpha));
        when(catalogService.getBySlugs(List.of())).thenReturn(List.of());
        when(catalogService.getProductImages(pinned.getId())).thenReturn(List.of());
        when(catalogService.getProductImages(alpha.getId())).thenReturn(List.of());

        CatalogueContentModels.StorefrontCollection collection = service.getPublishedStorefrontCollection("spring-drop");

        assertThat(collection.key()).isEqualTo("spring-drop");
        assertThat(collection.items()).extracting(CatalogueContentModels.StorefrontCollectionEntry::entityKey)
                .containsExactly("pinned-set", "alpha-set");
        assertThat(collection.items()).extracting(CatalogueContentModels.StorefrontCollectionEntry::source)
                .containsExactly("manual_pin", "backend_rule");
    }

    private Product product(String slug, String name, String description) {
        Product product = new Product(name, description, slug);
        product.setId(UUID.randomUUID());
        product.setCreatedAt(OffsetDateTime.parse("2026-04-15T07:00:00Z"));
        product.setUpdatedAt(OffsetDateTime.parse("2026-04-15T07:15:00Z"));
        return product;
    }

    private ProductVariant variant(Product product, String sku, long amount) {
        ProductVariant variant = new ProductVariant(sku, sku, Money.of(amount, "RUB"), 5);
        variant.setId(UUID.randomUUID());
        variant.setProduct(product);
        return variant;
    }
}
