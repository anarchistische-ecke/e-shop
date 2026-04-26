package com.example.api.catalog;

import com.example.api.content.CatalogueContentModels;
import com.example.api.content.CatalogueContentService;
import com.example.api.content.ContentModels;
import com.example.api.content.ContentAccessMode;
import com.example.catalog.domain.Category;
import com.example.catalog.domain.Product;
import com.example.catalog.domain.ProductVariant;
import com.example.catalog.repository.ProductVariantRepository;
import com.example.catalog.service.CatalogService;
import com.example.common.domain.Money;
import com.example.order.repository.OrderItemRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class CataloguePresentationService {

    private final CatalogueContentService catalogueContentService;
    private final CatalogService catalogService;
    private final OrderItemRepository orderItemRepository;
    private final ProductVariantRepository productVariantRepository;

    public CataloguePresentationService(
            CatalogueContentService catalogueContentService,
            CatalogService catalogService,
            OrderItemRepository orderItemRepository,
            ProductVariantRepository productVariantRepository
    ) {
        this.catalogueContentService = catalogueContentService;
        this.catalogService = catalogService;
        this.orderItemRepository = orderItemRepository;
        this.productVariantRepository = productVariantRepository;
    }

    public CataloguePresentationModels.OverlayMergeResult buildPublishedProductPresentation(Product product) {
        return buildProductPresentationResults(List.of(product), ContentAccessMode.PUBLISHED)
                .getOrDefault(normalizeKey(product != null ? product.getSlug() : null), fallbackProductResult(product, false, false));
    }

    public CataloguePresentationModels.OverlayMergeResult buildPreviewProductPresentation(Product product) {
        return buildProductPresentationResults(List.of(product), ContentAccessMode.PREVIEW)
                .getOrDefault(normalizeKey(product != null ? product.getSlug() : null), fallbackProductResult(product, true, false));
    }

    public Map<String, CataloguePresentationModels.OverlayMergeResult> buildPublishedProductPresentationResults(Collection<Product> products) {
        return buildProductPresentationResults(products, ContentAccessMode.PUBLISHED);
    }

    public Map<String, CataloguePresentationModels.OverlayMergeResult> buildPreviewProductPresentationResults(Collection<Product> products) {
        return buildProductPresentationResults(products, ContentAccessMode.PREVIEW);
    }

    public CataloguePresentationModels.OverlayMergeResult buildPublishedCategoryPresentation(Category category) {
        return buildCategoryPresentationResults(List.of(category), ContentAccessMode.PUBLISHED)
                .getOrDefault(normalizeKey(category != null ? category.getSlug() : null), fallbackCategoryResult(category, false, false));
    }

    public CataloguePresentationModels.OverlayMergeResult buildPreviewCategoryPresentation(Category category) {
        return buildCategoryPresentationResults(List.of(category), ContentAccessMode.PREVIEW)
                .getOrDefault(normalizeKey(category != null ? category.getSlug() : null), fallbackCategoryResult(category, true, false));
    }

    public Map<String, CataloguePresentationModels.OverlayMergeResult> buildPublishedCategoryPresentationResults(Collection<Category> categories) {
        return buildCategoryPresentationResults(categories, ContentAccessMode.PUBLISHED);
    }

    public Map<String, CataloguePresentationModels.OverlayMergeResult> buildPreviewCategoryPresentationResults(Collection<Category> categories) {
        return buildCategoryPresentationResults(categories, ContentAccessMode.PREVIEW);
    }

    public CatalogueContentModels.StorefrontCollection getPublishedStorefrontCollection(String key) {
        return buildStorefrontCollection(key, ContentAccessMode.PUBLISHED);
    }

    public CatalogueContentModels.StorefrontCollection getPreviewStorefrontCollection(String key) {
        return buildStorefrontCollection(key, ContentAccessMode.PREVIEW);
    }

    private Map<String, CataloguePresentationModels.OverlayMergeResult> buildProductPresentationResults(
            Collection<Product> products,
            ContentAccessMode accessMode
    ) {
        List<Product> safeProducts = products == null
                ? List.of()
                : products.stream().filter(Objects::nonNull).toList();

        if (safeProducts.isEmpty()) {
            return Map.of();
        }

        List<String> productKeys = safeProducts.stream()
                .map(Product::getSlug)
                .map(this::normalizeKey)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();

        Map<String, CatalogueContentModels.CatalogueOverlay> overlays = Map.of();
        boolean overlayReadFailed = false;
        try {
            overlays = accessMode.isPreview()
                    ? catalogueContentService.getPreviewProductOverlays(productKeys)
                    : catalogueContentService.getPublishedProductOverlays(productKeys);
        } catch (RuntimeException error) {
            overlayReadFailed = true;
        }

        Map<String, CatalogueContentModels.CatalogueOverlay> overlaysByKey = overlays;
        boolean finalOverlayReadFailed = overlayReadFailed;

        return safeProducts.stream()
                .collect(Collectors.toMap(
                        product -> normalizeKey(product.getSlug()),
                        product -> mergeProductPresentation(product, overlaysByKey.get(normalizeKey(product.getSlug())), accessMode, finalOverlayReadFailed),
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
    }

    private Map<String, CataloguePresentationModels.OverlayMergeResult> buildCategoryPresentationResults(
            Collection<Category> categories,
            ContentAccessMode accessMode
    ) {
        List<Category> safeCategories = categories == null
                ? List.of()
                : categories.stream().filter(Objects::nonNull).toList();

        if (safeCategories.isEmpty()) {
            return Map.of();
        }

        List<String> categoryKeys = safeCategories.stream()
                .map(Category::getSlug)
                .map(this::normalizeKey)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();

        Map<String, CatalogueContentModels.CatalogueOverlay> overlays = Map.of();
        boolean overlayReadFailed = false;
        try {
            overlays = accessMode.isPreview()
                    ? catalogueContentService.getPreviewCategoryOverlays(categoryKeys)
                    : catalogueContentService.getPublishedCategoryOverlays(categoryKeys);
        } catch (RuntimeException error) {
            overlayReadFailed = true;
        }

        Map<String, CatalogueContentModels.CatalogueOverlay> overlaysByKey = overlays;
        boolean finalOverlayReadFailed = overlayReadFailed;

        return safeCategories.stream()
                .collect(Collectors.toMap(
                        category -> normalizeKey(category.getSlug()),
                        category -> mergeCategoryPresentation(category, overlaysByKey.get(normalizeKey(category.getSlug())), accessMode, finalOverlayReadFailed),
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
    }

    private CataloguePresentationModels.OverlayMergeResult mergeProductPresentation(
            Product product,
            CatalogueContentModels.CatalogueOverlay overlay,
            ContentAccessMode accessMode,
            boolean overlayReadFailed
    ) {
        CatalogueContentModels.CataloguePresentation fallback = new CatalogueContentModels.CataloguePresentation(
                sourceMetadata(accessMode, null, overlayReadFailed),
                product != null ? product.getName() : null,
                product != null ? product.getDescription() : null,
                null,
                null,
                null,
                product != null ? product.getName() : null,
                product != null && product.getBrand() != null ? product.getBrand().getName() : null,
                product != null ? product.getDescription() : null,
                buildFallbackHero(
                        "product",
                        product != null ? product.getSlug() : null,
                        product != null ? product.getName() : null,
                        product != null ? product.getBrand() != null ? product.getBrand().getName() : null : null,
                        product != null ? product.getDescription() : null,
                        null
                ),
                List.of(),
                List.of()
        );

        if (overlay == null) {
            return new CataloguePresentationModels.OverlayMergeResult(fallback, overlayReadFailed);
        }

        CatalogueContentModels.CataloguePresentation merged = new CatalogueContentModels.CataloguePresentation(
                sourceMetadata(accessMode, overlay, overlayReadFailed),
                firstText(overlay.seoTitle(), fallback.seoTitle()),
                firstText(overlay.seoDescription(), fallback.seoDescription()),
                overlay.seoImage() != null ? overlay.seoImage() : fallback.seoImage(),
                firstText(overlay.badgeText(), fallback.badgeText()),
                firstText(overlay.ribbonText(), fallback.ribbonText()),
                firstText(overlay.marketingTitle(), fallback.marketingTitle()),
                firstText(overlay.marketingSubtitle(), fallback.marketingSubtitle()),
                firstText(overlay.introBody(), fallback.introBody()),
                mergeSection(fallback.hero(), overlay.hero()),
                overlay.blocks() != null ? overlay.blocks() : List.of(),
                overlay.linkedCollectionKeys() != null ? overlay.linkedCollectionKeys() : List.of()
        );

        return new CataloguePresentationModels.OverlayMergeResult(merged, overlayReadFailed);
    }

    private CataloguePresentationModels.OverlayMergeResult mergeCategoryPresentation(
            Category category,
            CatalogueContentModels.CatalogueOverlay overlay,
            ContentAccessMode accessMode,
            boolean overlayReadFailed
    ) {
        ContentModels.MediaAsset fallbackImage = StringUtils.hasText(category != null ? category.getImageUrl() : null)
                ? new ContentModels.MediaAsset(null, category.getImageUrl(), null, null, category.getName(), null)
                : null;

        CatalogueContentModels.CataloguePresentation fallback = new CatalogueContentModels.CataloguePresentation(
                sourceMetadata(accessMode, null, overlayReadFailed),
                category != null ? category.getName() : null,
                category != null ? category.getDescription() : null,
                fallbackImage,
                null,
                null,
                category != null ? category.getName() : null,
                null,
                category != null ? category.getDescription() : null,
                buildFallbackHero(
                        "category",
                        category != null ? category.getSlug() : null,
                        category != null ? category.getName() : null,
                        null,
                        category != null ? category.getDescription() : null,
                        fallbackImage
                ),
                List.of(),
                List.of()
        );

        if (overlay == null) {
            return new CataloguePresentationModels.OverlayMergeResult(fallback, overlayReadFailed);
        }

        CatalogueContentModels.CataloguePresentation merged = new CatalogueContentModels.CataloguePresentation(
                sourceMetadata(accessMode, overlay, overlayReadFailed),
                firstText(overlay.seoTitle(), fallback.seoTitle()),
                firstText(overlay.seoDescription(), fallback.seoDescription()),
                overlay.seoImage() != null ? overlay.seoImage() : fallback.seoImage(),
                firstText(overlay.badgeText(), fallback.badgeText()),
                firstText(overlay.ribbonText(), fallback.ribbonText()),
                firstText(overlay.marketingTitle(), fallback.marketingTitle()),
                firstText(overlay.marketingSubtitle(), fallback.marketingSubtitle()),
                firstText(overlay.introBody(), fallback.introBody()),
                mergeSection(fallback.hero(), overlay.hero()),
                overlay.blocks() != null ? overlay.blocks() : List.of(),
                overlay.linkedCollectionKeys() != null ? overlay.linkedCollectionKeys() : List.of()
        );

        return new CataloguePresentationModels.OverlayMergeResult(merged, overlayReadFailed);
    }

    private CatalogueContentModels.StorefrontCollection buildStorefrontCollection(String key, ContentAccessMode accessMode) {
        CatalogueContentModels.StorefrontCollectionDefinition definition = accessMode.isPreview()
                ? catalogueContentService.getPreviewStorefrontCollection(key)
                : catalogueContentService.getStorefrontCollection(key);

        List<CollectionEntityRef> resolvedRefs = resolveStorefrontCollectionRefs(definition);

        List<String> productKeys = resolvedRefs.stream()
                .filter(ref -> "product".equals(ref.entityKind()))
                .map(CollectionEntityRef::entityKey)
                .distinct()
                .toList();
        List<String> categoryKeys = resolvedRefs.stream()
                .filter(ref -> "category".equals(ref.entityKind()))
                .map(CollectionEntityRef::entityKey)
                .distinct()
                .toList();

        Map<String, Product> productsByKey = catalogService.getProductsBySlugs(productKeys).stream()
                .filter(product -> product.isIsActive())
                .collect(Collectors.toMap(
                        product -> normalizeKey(product.getSlug()),
                        Function.identity(),
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
        Map<String, Category> categoriesByKey = catalogService.getBySlugs(categoryKeys).stream()
                .filter(Category::isIsActive)
                .collect(Collectors.toMap(
                        category -> normalizeKey(category.getSlug()),
                        Function.identity(),
                        (left, right) -> left,
                        LinkedHashMap::new
                ));

        Map<String, CataloguePresentationModels.OverlayMergeResult> productPresentations = accessMode.isPreview()
                ? buildPreviewProductPresentationResults(productsByKey.values())
                : buildPublishedProductPresentationResults(productsByKey.values());
        Map<String, CataloguePresentationModels.OverlayMergeResult> categoryPresentations = accessMode.isPreview()
                ? buildPreviewCategoryPresentationResults(categoriesByKey.values())
                : buildPublishedCategoryPresentationResults(categoriesByKey.values());

        List<CatalogueContentModels.StorefrontCollectionEntry> items = resolvedRefs.stream()
                .map(ref -> toCollectionEntry(ref, productsByKey, categoriesByKey, productPresentations, categoryPresentations))
                .filter(Objects::nonNull)
                .toList();

        return new CatalogueContentModels.StorefrontCollection(
                definition.key(),
                definition.title(),
                definition.description(),
                definition.mode(),
                definition.ruleType(),
                definition.categoryKey(),
                definition.brandKey(),
                definition.limit(),
                definition.sortMode(),
                definition.seoTitle(),
                definition.seoDescription(),
                definition.seoImage(),
                definition.hero(),
                definition.primaryCtaLabel(),
                definition.primaryCtaUrl(),
                definition.publishedAt(),
                items
        );
    }

    private List<CollectionEntityRef> resolveStorefrontCollectionRefs(CatalogueContentModels.StorefrontCollectionDefinition definition) {
        String mode = normalizeKey(definition.mode());
        List<CollectionEntityRef> backendRuleRefs = switch (mode) {
            case "backend_rule", "hybrid" -> resolveBackendRule(definition);
            default -> List.of();
        };

        Map<String, CollectionEntityRef> ordered = new LinkedHashMap<>();
        for (CollectionEntityRef ref : backendRuleRefs) {
            ordered.put(ref.uniqueKey(), ref);
        }

        List<CatalogueContentModels.StorefrontCollectionRuleItem> rules = definition.rules() != null
                ? definition.rules()
                : List.of();

        List<CatalogueContentModels.StorefrontCollectionRuleItem> pinnedRules = rules.stream()
                .filter(rule -> !"exclude".equals(normalizeKey(rule.behavior())))
                .sorted(Comparator.comparing(CatalogueContentModels.StorefrontCollectionRuleItem::sort, Comparator.nullsLast(Integer::compareTo))
                        .thenComparing(CatalogueContentModels.StorefrontCollectionRuleItem::entityKey, Comparator.nullsLast(String::compareToIgnoreCase)))
                .toList();

        List<CatalogueContentModels.StorefrontCollectionRuleItem> excludedRules = rules.stream()
                .filter(rule -> "exclude".equals(normalizeKey(rule.behavior())))
                .toList();

        if ("manual".equals(mode) || "hybrid".equals(mode)) {
            for (int index = pinnedRules.size() - 1; index >= 0; index -= 1) {
                CatalogueContentModels.StorefrontCollectionRuleItem rule = pinnedRules.get(index);
                CollectionEntityRef ref = new CollectionEntityRef(rule.entityKind(), rule.entityKey(), "manual_pin");
                ordered.remove(ref.uniqueKey());
                LinkedHashMap<String, CollectionEntityRef> next = new LinkedHashMap<>();
                next.put(ref.uniqueKey(), ref);
                next.putAll(ordered);
                ordered = next;
            }
        }

        for (CatalogueContentModels.StorefrontCollectionRuleItem rule : excludedRules) {
            ordered.remove(new CollectionEntityRef(rule.entityKind(), rule.entityKey(), "exclude").uniqueKey());
        }

        int limit = definition.limit() != null && definition.limit() > 0 ? definition.limit() : 12;
        return ordered.values().stream()
                .limit(limit)
                .toList();
    }

    private List<CollectionEntityRef> resolveBackendRule(CatalogueContentModels.StorefrontCollectionDefinition definition) {
        String ruleType = normalizeKey(definition.ruleType());
        return switch (ruleType) {
            case "category" -> catalogService.getProducts(definition.categoryKey(), definition.brandKey()).stream()
                    .filter(Product::isIsActive)
                    .map(product -> new CollectionEntityRef("product", normalizeKey(product.getSlug()), "backend_rule"))
                    .toList();
            case "brand" -> catalogService.getProducts(null, definition.brandKey()).stream()
                    .filter(Product::isIsActive)
                    .map(product -> new CollectionEntityRef("product", normalizeKey(product.getSlug()), "backend_rule"))
                    .toList();
            case "new" -> sortProducts(catalogService.getAllProducts(), definition.sortMode()).stream()
                    .filter(Product::isIsActive)
                    .map(product -> new CollectionEntityRef("product", normalizeKey(product.getSlug()), "backend_rule"))
                    .toList();
            case "bestsellers" -> resolveBestsellerProducts(definition.limit() != null ? definition.limit() : 12).stream()
                    .filter(Product::isIsActive)
                    .map(product -> new CollectionEntityRef("product", normalizeKey(product.getSlug()), "backend_rule"))
                    .toList();
            case "sale" -> List.of();
            default -> List.of();
        };
    }

    private List<Product> resolveBestsellerProducts(int limit) {
        List<OrderItemRepository.VariantSalesView> variantSales = orderItemRepository.findTopVariantSales(PageRequest.of(0, Math.max(1, limit * 4)));
        if (variantSales.isEmpty()) {
            return List.of();
        }

        List<UUID> variantIds = variantSales.stream()
                .map(OrderItemRepository.VariantSalesView::getVariantId)
                .filter(Objects::nonNull)
                .toList();

        Map<UUID, ProductVariant> variantsById = productVariantRepository.findByIdIn(variantIds).stream()
                .collect(Collectors.toMap(ProductVariant::getId, Function.identity(), (left, right) -> left));

        LinkedHashMap<String, Product> products = new LinkedHashMap<>();
        for (OrderItemRepository.VariantSalesView salesView : variantSales) {
            ProductVariant variant = variantsById.get(salesView.getVariantId());
            if (variant == null || variant.getProduct() == null) {
                continue;
            }
            Product product = variant.getProduct();
            products.putIfAbsent(normalizeKey(product.getSlug()), product);
            if (products.size() >= limit) {
                break;
            }
        }

        return products.values().stream().toList();
    }

    private List<Product> sortProducts(List<Product> products, String sortMode) {
        String normalizedSortMode = normalizeKey(sortMode);
        Comparator<Product> comparator = switch (normalizedSortMode == null ? "default" : normalizedSortMode) {
            case "alphabetical" -> Comparator.comparing(Product::getName, Comparator.nullsLast(String::compareToIgnoreCase));
            case "price_asc" -> Comparator.comparing(this::primaryPriceAmount, Comparator.nullsLast(Long::compareTo));
            case "price_desc" -> Comparator.comparing(this::primaryPriceAmount, Comparator.nullsLast(Long::compareTo)).reversed();
            case "oldest" -> Comparator.comparing(Product::getCreatedAt, Comparator.nullsLast(java.time.OffsetDateTime::compareTo));
            case "newest", "default" -> Comparator.comparing(Product::getCreatedAt, Comparator.nullsLast(java.time.OffsetDateTime::compareTo)).reversed();
            default -> Comparator.comparing(Product::getCreatedAt, Comparator.nullsLast(java.time.OffsetDateTime::compareTo)).reversed();
        };

        return products.stream()
                .sorted(comparator)
                .toList();
    }

    private Long primaryPriceAmount(Product product) {
        if (product == null || product.getVariants() == null || product.getVariants().isEmpty()) {
            return null;
        }

        return product.getVariants().stream()
                .map(ProductVariant::getPrice)
                .filter(Objects::nonNull)
                .map(Money::getAmount)
                .min(Long::compareTo)
                .orElse(null);
    }

    private CatalogueContentModels.StorefrontCollectionEntry toCollectionEntry(
            CollectionEntityRef ref,
            Map<String, Product> productsByKey,
            Map<String, Category> categoriesByKey,
            Map<String, CataloguePresentationModels.OverlayMergeResult> productPresentations,
            Map<String, CataloguePresentationModels.OverlayMergeResult> categoryPresentations
    ) {
        if ("category".equals(ref.entityKind())) {
            Category category = categoriesByKey.get(ref.entityKey());
            if (category == null) {
                return null;
            }
            CatalogueContentModels.CataloguePresentation presentation = categoryPresentations.getOrDefault(
                    ref.entityKey(),
                    fallbackCategoryResult(category, false, false)
            ).presentation();
            ContentModels.MediaAsset image = StringUtils.hasText(category.getImageUrl())
                    ? new ContentModels.MediaAsset(null, category.getImageUrl(), null, null, category.getName(), null)
                    : null;
            return new CatalogueContentModels.StorefrontCollectionEntry(
                    "category",
                    ref.entityKey(),
                    category.getId() != null ? category.getId().toString() : null,
                    ref.source(),
                    category.getName(),
                    category.getDescription(),
                    category.getSlug(),
                    "/category/" + category.getSlug(),
                    image,
                    null,
                    null,
                    List.of(),
                    presentation
            );
        }

        Product product = productsByKey.get(ref.entityKey());
        if (product == null) {
            return null;
        }
        CatalogueContentModels.CataloguePresentation presentation = productPresentations.getOrDefault(
                ref.entityKey(),
                fallbackProductResult(product, false, false)
        ).presentation();
        ContentModels.MediaAsset image = product.getImages() != null && !product.getImages().isEmpty()
                ? null
                : null;
        String imageUrl = catalogService.getProductImages(product.getId()).stream()
                .findFirst()
                .map(productImage -> productImage.getUrl())
                .orElse(null);
        if (StringUtils.hasText(imageUrl)) {
            image = new ContentModels.MediaAsset(null, imageUrl, null, null, product.getName(), null);
        }
        Money price = product.getVariants() == null
                ? null
                : product.getVariants().stream()
                .map(ProductVariant::getPrice)
                .filter(Objects::nonNull)
                .min(Comparator.comparingLong(Money::getAmount))
                .orElse(null);

        return new CatalogueContentModels.StorefrontCollectionEntry(
                "product",
                ref.entityKey(),
                product.getId() != null ? product.getId().toString() : null,
                ref.source(),
                firstText(presentation.marketingTitle(), product.getName()),
                firstText(presentation.introBody(), product.getDescription()),
                product.getSlug(),
                "/product/" + product.getId() + "/" + product.getSlug(),
                image,
                price,
                product.getBrand() != null ? normalizeKey(product.getBrand().getSlug()) : null,
                product.getCategories() != null
                        ? product.getCategories().stream()
                        .map(Category::getSlug)
                        .map(this::normalizeKey)
                        .filter(StringUtils::hasText)
                        .toList()
                        : List.of(),
                presentation
        );
    }

    private CatalogueContentModels.PresentationSource sourceMetadata(
            ContentAccessMode accessMode,
            CatalogueContentModels.CatalogueOverlay overlay,
            boolean overlayReadFailed
    ) {
        String mode;
        if (overlay != null) {
            mode = accessMode.isPreview() ? "preview_overlay" : "published_overlay";
        } else if (overlayReadFailed) {
            mode = "backend_fallback";
        } else {
            mode = "backend_only";
        }

        return new CatalogueContentModels.PresentationSource(
                mode,
                overlay != null ? overlay.key() : null,
                overlay != null ? overlay.status() : null,
                overlay != null ? overlay.publishedAt() : null,
                overlay != null,
                !overlayReadFailed
        );
    }

    private ContentModels.PageSection buildFallbackHero(
            String ownerKind,
            String ownerKey,
            String title,
            String accent,
            String body,
            ContentModels.MediaAsset image
    ) {
        if (!StringUtils.hasText(title) && !StringUtils.hasText(accent) && !StringUtils.hasText(body) && image == null) {
            return null;
        }

        return new ContentModels.PageSection(
                ownerKind + "-fallback-hero:" + normalizeKey(ownerKey),
                "hero",
                0,
                "hero",
                null,
                title,
                accent,
                body,
                image,
                null,
                null,
                null,
                null,
                null,
                "default",
                "contained",
                null,
                List.of()
        );
    }

    private ContentModels.PageSection mergeSection(ContentModels.PageSection fallback, ContentModels.PageSection overlay) {
        if (overlay == null) {
            return fallback;
        }
        if (fallback == null) {
            return overlay;
        }
        return new ContentModels.PageSection(
                firstText(overlay.internalName(), fallback.internalName()),
                firstText(overlay.sectionType(), fallback.sectionType()),
                overlay.sort() != null ? overlay.sort() : fallback.sort(),
                firstText(overlay.anchorId(), fallback.anchorId()),
                firstText(overlay.eyebrow(), fallback.eyebrow()),
                firstText(overlay.title(), fallback.title()),
                firstText(overlay.accent(), fallback.accent()),
                firstText(overlay.body(), fallback.body()),
                overlay.image() != null ? overlay.image() : fallback.image(),
                overlay.mobileImage() != null ? overlay.mobileImage() : fallback.mobileImage(),
                firstText(overlay.primaryCtaLabel(), fallback.primaryCtaLabel()),
                firstText(overlay.primaryCtaUrl(), fallback.primaryCtaUrl()),
                firstText(overlay.secondaryCtaLabel(), fallback.secondaryCtaLabel()),
                firstText(overlay.secondaryCtaUrl(), fallback.secondaryCtaUrl()),
                firstText(overlay.styleVariant(), fallback.styleVariant()),
                firstText(overlay.layoutVariant(), fallback.layoutVariant()),
                overlay.publishedAt() != null ? overlay.publishedAt() : fallback.publishedAt(),
                overlay.items() != null && !overlay.items().isEmpty() ? overlay.items() : fallback.items()
        );
    }

    private CataloguePresentationModels.OverlayMergeResult fallbackProductResult(Product product, boolean preview, boolean overlayReadFailed) {
        return mergeProductPresentation(product, null, preview ? ContentAccessMode.PREVIEW : ContentAccessMode.PUBLISHED, overlayReadFailed);
    }

    private CataloguePresentationModels.OverlayMergeResult fallbackCategoryResult(Category category, boolean preview, boolean overlayReadFailed) {
        return mergeCategoryPresentation(category, null, preview ? ContentAccessMode.PREVIEW : ContentAccessMode.PUBLISHED, overlayReadFailed);
    }

    private String firstText(String preferred, String fallback) {
        if (StringUtils.hasText(preferred)) {
            return preferred.trim();
        }

        if (StringUtils.hasText(fallback)) {
            return fallback.trim();
        }

        return null;
    }

    private String normalizeKey(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }

        return value.trim().toLowerCase(Locale.ROOT);
    }

    private record CollectionEntityRef(String entityKind, String entityKey, String source) {
        private String uniqueKey() {
            return entityKind + ":" + entityKey;
        }
    }
}
