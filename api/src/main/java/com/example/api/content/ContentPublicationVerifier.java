package com.example.api.content;

import com.example.catalog.domain.Category;
import com.example.catalog.domain.Product;
import com.example.catalog.service.CatalogService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class ContentPublicationVerifier {

    private final DirectusContentCacheService contentCacheService;
    private final ContentService contentService;
    private final CatalogueContentService catalogueContentService;
    private final CatalogService catalogService;

    public ContentPublicationVerifier(
            DirectusContentCacheService contentCacheService,
            ContentService contentService,
            CatalogueContentService catalogueContentService,
            CatalogService catalogService
    ) {
        this.contentCacheService = contentCacheService;
        this.contentService = contentService;
        this.catalogueContentService = catalogueContentService;
        this.catalogService = catalogService;
    }

    public PublicationCheckResult verifyPage(String rawSlug, List<ExpectedReference> expectedReferences) {
        String slug = requireKey(rawSlug, "Page slug is required");
        DirectusContentCacheService.CacheInvalidationResult invalidation = contentCacheService.invalidatePage(slug);
        if (!invalidation.successful()) {
            return new PublicationCheckResult(
                    false,
                    false,
                    invalidation,
                    List.of(new ReferenceIssue("cache", slug, invalidation.error())),
                    0,
                    0
            );
        }

        ContentModels.Page page;
        try {
            page = contentService.getPageBySlug(slug);
        } catch (RuntimeException error) {
            return new PublicationCheckResult(
                    false,
                    false,
                    invalidation,
                    List.of(new ReferenceIssue("page", slug, "not_published")),
                    0,
                    0
            );
        }

        Set<ExpectedReference> publishedReferences = collectReferences(page);
        Set<ExpectedReference> normalizedExpectedReferences = new LinkedHashSet<>(normalizeReferences(expectedReferences));
        List<ReferenceIssue> issues = new ArrayList<>();
        for (ExpectedReference expected : normalizedExpectedReferences) {
            if (!publishedReferences.contains(expected)) {
                issues.add(new ReferenceIssue(expected.kind(), expected.key(), "not_in_published_page"));
            }
        }
        for (ExpectedReference published : publishedReferences) {
            if (!normalizedExpectedReferences.contains(published)) {
                issues.add(new ReferenceIssue(published.kind(), published.key(), "unexpected_in_published_page"));
            }
        }
        publishedReferences.forEach(reference -> validateReference(reference, issues));

        int sectionCount = page.sections() != null ? page.sections().size() : 0;
        return new PublicationCheckResult(
                issues.isEmpty(),
                true,
                invalidation,
                List.copyOf(issues),
                sectionCount,
                publishedReferences.size()
        );
    }

    private Set<ExpectedReference> collectReferences(ContentModels.Page page) {
        Set<ExpectedReference> references = new LinkedHashSet<>();
        if (page == null || page.sections() == null) {
            return references;
        }
        page.sections().forEach(section -> {
            if (section.items() == null) return;
            section.items().forEach(item -> {
                String kind = normalizeKind(item.referenceKind());
                String key = normalizeKey(item.referenceKey());
                if (isVerifiableKind(kind) && StringUtils.hasText(key)) {
                    references.add(new ExpectedReference(kind, key));
                }
            });
        });
        return references;
    }

    private List<ExpectedReference> normalizeReferences(List<ExpectedReference> references) {
        if (references == null) return List.of();
        return references.stream()
                .filter(reference -> reference != null && StringUtils.hasText(reference.key()))
                .map(reference -> new ExpectedReference(normalizeKind(reference.kind()), normalizeKey(reference.key())))
                .filter(reference -> isVerifiableKind(reference.kind()))
                .distinct()
                .toList();
    }

    private void validateReference(ExpectedReference reference, List<ReferenceIssue> issues) {
        switch (reference.kind()) {
            case "product_slug", "product_id" -> validateProduct(reference, issues);
            case "category_slug", "category_id" -> validateCategory(reference, issues);
            case "storefront_collection" -> validateCollection(reference, issues);
            default -> {
            }
        }
    }

    private void validateProduct(ExpectedReference reference, List<ReferenceIssue> issues) {
        Optional<Product> product = "product_id".equals(reference.kind())
                ? parseUuid(reference.key()).flatMap(catalogService::getProduct)
                : catalogService.getProductBySlug(reference.key());
        if (product.isEmpty()) {
            issues.add(new ReferenceIssue(reference.kind(), reference.key(), "missing"));
        } else if (!product.get().isIsActive()) {
            issues.add(new ReferenceIssue(reference.kind(), reference.key(), "inactive"));
        }
    }

    private void validateCategory(ExpectedReference reference, List<ReferenceIssue> issues) {
        Optional<Category> category = "category_id".equals(reference.kind())
                ? parseUuid(reference.key()).flatMap(catalogService::getByCategoryId)
                : catalogService.getBySlug(reference.key());
        if (category.isEmpty()) {
            issues.add(new ReferenceIssue(reference.kind(), reference.key(), "missing"));
        } else if (!category.get().isIsActive()) {
            issues.add(new ReferenceIssue(reference.kind(), reference.key(), "inactive"));
        }
    }

    private void validateCollection(ExpectedReference reference, List<ReferenceIssue> issues) {
        try {
            CatalogueContentModels.StorefrontCollectionDefinition collection =
                    catalogueContentService.getStorefrontCollection(reference.key());
            if (collection == null || !"published".equalsIgnoreCase(collection.status())) {
                issues.add(new ReferenceIssue(reference.kind(), reference.key(), "not_published"));
            }
        } catch (RuntimeException error) {
            issues.add(new ReferenceIssue(reference.kind(), reference.key(), "missing"));
        }
    }

    private Optional<UUID> parseUuid(String value) {
        try {
            return Optional.of(UUID.fromString(value));
        } catch (IllegalArgumentException error) {
            return Optional.empty();
        }
    }

    private boolean isVerifiableKind(String kind) {
        return Set.of(
                "product_slug",
                "product_id",
                "category_slug",
                "category_id",
                "storefront_collection"
        ).contains(kind);
    }

    private String normalizeKind(String value) {
        String kind = normalizeKey(value).toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        return switch (kind) {
            case "product" -> "product_slug";
            case "category" -> "category_slug";
            case "collection", "cms_collection", "collection_key" -> "storefront_collection";
            default -> kind;
        };
    }

    private String normalizeKey(String value) {
        return value == null ? "" : value.trim();
    }

    private String requireKey(String value, String message) {
        if (!StringUtils.hasText(value)) throw new IllegalArgumentException(message);
        return value.trim().toLowerCase(Locale.ROOT);
    }

    public record ExpectedReference(String kind, String key) {
    }

    public record ReferenceIssue(String kind, String key, String reason) {
    }

    public record PublicationCheckResult(
            boolean successful,
            boolean published,
            DirectusContentCacheService.CacheInvalidationResult cacheInvalidation,
            List<ReferenceIssue> issues,
            int sectionCount,
            int referenceCount
    ) {
    }
}
