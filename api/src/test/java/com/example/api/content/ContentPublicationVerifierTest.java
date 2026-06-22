package com.example.api.content;

import com.example.catalog.domain.Product;
import com.example.catalog.service.CatalogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ContentPublicationVerifierTest {

    @Mock
    private DirectusContentCacheService cacheService;
    @Mock
    private ContentService contentService;
    @Mock
    private CatalogueContentService catalogueContentService;
    @Mock
    private CatalogService catalogService;

    private ContentPublicationVerifier verifier;

    @BeforeEach
    void setUp() {
        verifier = new ContentPublicationVerifier(
                cacheService,
                contentService,
                catalogueContentService,
                catalogService
        );
    }

    @Test
    void verifiesPublishedActiveProductReferences() {
        when(cacheService.invalidatePage("home")).thenReturn(invalidation(true, null));
        when(contentService.getPageBySlug("home")).thenReturn(pageWithProduct("linen-duvet"));
        Product product = new Product("Linen", "", "linen-duvet");
        product.setIsActive(true);
        when(catalogService.getProductBySlug("linen-duvet")).thenReturn(Optional.of(product));

        ContentPublicationVerifier.PublicationCheckResult result = verifier.verifyPage(
                "home",
                List.of(new ContentPublicationVerifier.ExpectedReference("product_slug", "linen-duvet"))
        );

        assertThat(result.successful()).isTrue();
        assertThat(result.published()).isTrue();
        assertThat(result.issues()).isEmpty();
        assertThat(result.referenceCount()).isEqualTo(1);
    }

    @Test
    void reportsExpectedReferencesMissingFromPublishedPage() {
        when(cacheService.invalidatePage("home")).thenReturn(invalidation(true, null));
        when(contentService.getPageBySlug("home")).thenReturn(pageWithProduct("linen-duvet"));
        Product product = new Product("Linen", "", "linen-duvet");
        product.setIsActive(true);
        when(catalogService.getProductBySlug("linen-duvet")).thenReturn(Optional.of(product));

        ContentPublicationVerifier.PublicationCheckResult result = verifier.verifyPage(
                "home",
                List.of(new ContentPublicationVerifier.ExpectedReference("product_slug", "missing-product"))
        );

        assertThat(result.successful()).isFalse();
        assertThat(result.issues()).contains(
                new ContentPublicationVerifier.ReferenceIssue("product_slug", "missing-product", "not_in_published_page")
        );
    }

    @Test
    void reportsReferencesThatShouldHaveBeenRemovedFromPublishedPage() {
        when(cacheService.invalidatePage("home")).thenReturn(invalidation(true, null));
        when(contentService.getPageBySlug("home")).thenReturn(pageWithProduct("old-product"));
        Product product = new Product("Old product", "", "old-product");
        product.setIsActive(true);
        when(catalogService.getProductBySlug("old-product")).thenReturn(Optional.of(product));

        ContentPublicationVerifier.PublicationCheckResult result = verifier.verifyPage("home", List.of());

        assertThat(result.successful()).isFalse();
        assertThat(result.issues()).contains(
                new ContentPublicationVerifier.ReferenceIssue(
                        "product_slug",
                        "old-product",
                        "unexpected_in_published_page"
                )
        );
    }

    @Test
    void stopsPublicationVerificationWhenCacheInvalidationFails() {
        when(cacheService.invalidatePage("home")).thenReturn(invalidation(false, "Redis delete failed"));

        ContentPublicationVerifier.PublicationCheckResult result = verifier.verifyPage("home", List.of());

        assertThat(result.successful()).isFalse();
        assertThat(result.published()).isFalse();
        assertThat(result.issues()).containsExactly(
                new ContentPublicationVerifier.ReferenceIssue("cache", "home", "Redis delete failed")
        );
    }

    private DirectusContentCacheService.CacheInvalidationResult invalidation(boolean successful, String error) {
        return new DirectusContentCacheService.CacheInvalidationResult(
                "page", "cms:content", List.of("cms:content:page:home"), 1, successful, error
        );
    }

    private ContentModels.Page pageWithProduct(String key) {
        ContentModels.PageSectionItem item = new ContentModels.PageSectionItem(
                null, null, null, null, null, "product_slug", key, 1, null
        );
        ContentModels.PageSection section = new ContentModels.PageSection(
                "Products", "product_reference_list", 1, null, null, "Products", null, null,
                null, null, null, null, null, null, "default", "cards", null, List.of(item)
        );
        return new ContentModels.Page(
                "home", "/", "Home", "home", "Home", null, null, null, null, null, List.of(section)
        );
    }
}
