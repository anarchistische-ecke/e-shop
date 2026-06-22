package com.example.api.catalog;

import com.example.catalog.domain.Category;
import com.example.catalog.domain.Product;
import com.example.catalog.domain.ProductImage;
import com.example.catalog.domain.ProductVariant;
import com.example.catalog.service.CatalogService;
import com.example.common.domain.Money;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/catalogue/cards")
public class StorefrontCardController {

    private static final int DEFAULT_PRODUCT_LIMIT = 0;
    private static final int DEFAULT_CATEGORY_LIMIT = 0;
    private static final int MAX_LIMIT = 48;

    private final CatalogService catalogService;
    private final CatalogueResponseFactory responseFactory;

    public StorefrontCardController(
            CatalogService catalogService,
            CatalogueResponseFactory responseFactory
    ) {
        this.catalogService = catalogService;
        this.responseFactory = responseFactory;
    }

    @GetMapping
    public ResponseEntity<StorefrontCardsResponse> getCards(
            @RequestParam(value = "productKeys", required = false) String productKeys,
            @RequestParam(value = "categoryKeys", required = false) String categoryKeys,
            @RequestParam(value = "productLimit", required = false, defaultValue = "" + DEFAULT_PRODUCT_LIMIT) int productLimit,
            @RequestParam(value = "categoryLimit", required = false, defaultValue = "" + DEFAULT_CATEGORY_LIMIT) int categoryLimit
    ) {
        List<Product> products = resolveProducts(parseKeys(productKeys), clampLimit(productLimit));
        List<Category> categories = resolveCategories(parseKeys(categoryKeys), clampLimit(categoryLimit));

        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(new StorefrontCardsResponse(
                        products.stream().map(this::toProductCard).toList(),
                        categories.stream().map(this::toCategoryCard).toList(),
                        true
                ));
    }

    private List<Product> resolveProducts(List<String> keys, int limit) {
        LinkedHashMap<String, Product> ordered = new LinkedHashMap<>();
        for (String key : keys) {
            resolveProduct(key)
                    .filter(Product::isIsActive)
                    .ifPresent(product -> ordered.putIfAbsent(productKey(product), product));
        }

        if (ordered.isEmpty() && limit > 0) {
            catalogService.getAllProducts().stream()
                    .filter(Product::isIsActive)
                    .limit(limit)
                    .forEach(product -> ordered.putIfAbsent(productKey(product), product));
        }

        return ordered.values().stream().limit(limit > 0 ? limit : MAX_LIMIT).toList();
    }

    private List<Category> resolveCategories(List<String> keys, int limit) {
        LinkedHashMap<String, Category> ordered = new LinkedHashMap<>();
        for (String key : keys) {
            resolveCategory(key)
                    .filter(Category::isIsActive)
                    .ifPresent(category -> ordered.putIfAbsent(categoryKey(category), category));
        }

        if (ordered.isEmpty() && limit > 0) {
            catalogService.listAllInCategory().stream()
                    .filter(Category::isIsActive)
                    .limit(limit)
                    .forEach(category -> ordered.putIfAbsent(categoryKey(category), category));
        }

        return ordered.values().stream().limit(limit > 0 ? limit : MAX_LIMIT).toList();
    }

    private Optional<Product> resolveProduct(String key) {
        if (!StringUtils.hasText(key)) {
            return Optional.empty();
        }
        try {
            return catalogService.getProduct(UUID.fromString(key.trim()));
        } catch (IllegalArgumentException ignored) {
            return catalogService.getProductBySlug(key.trim());
        }
    }

    private Optional<Category> resolveCategory(String key) {
        if (!StringUtils.hasText(key)) {
            return Optional.empty();
        }
        try {
            return catalogService.getByCategoryId(UUID.fromString(key.trim()));
        } catch (IllegalArgumentException ignored) {
            return catalogService.getBySlug(key.trim());
        }
    }

    private ProductCardResponse toProductCard(Product product) {
        List<ProductImage> images = catalogService.getProductImages(product.getId());
        ProductImage primaryImage = images.stream().findFirst().orElse(null);
        ProductVariant primaryVariant = primaryVariant(product);
        CatalogController.VariantResponse primaryVariantResponse = primaryVariant != null
                ? responseFactory.toVariantResponse(primaryVariant)
                : null;

        List<CatalogController.ImageResponse> imageResponses = primaryImage != null
                ? List.of(responseFactory.toImageResponse(primaryImage))
                : List.of();

        return new ProductCardResponse(
                product.getId(),
                product.getSlug(),
                product.getName(),
                summarize(product.getDescription()),
                firstCategorySlug(product),
                categorySummaries(product),
                product.getBrand() != null ? product.getBrand().getSlug() : null,
                primaryVariantResponse != null ? primaryVariantResponse.getPrice() : null,
                primaryVariantResponse != null ? primaryVariantResponse.getOldPrice() : null,
                primaryVariantResponse != null && primaryVariantResponse.isOnSale(),
                primaryVariantResponse != null ? primaryVariantResponse.getDiscountPercent() : null,
                totalStock(product),
                imageResponses,
                primaryImage != null ? responseFactory.toMediaManifest(primaryImage, product.getName()) : null
        );
    }

    private CategoryCardResponse toCategoryCard(Category category) {
        int productCount = catalogService.getProducts(category.getSlug(), null).stream()
                .filter(Product::isIsActive)
                .toList()
                .size();

        return new CategoryCardResponse(
                category.getId(),
                category.getName(),
                category.getSlug(),
                summarize(category.getDescription()),
                category.getImageUrl(),
                category.getParent() != null ? category.getParent().getId() : null,
                category.getPosition(),
                category.isIsActive(),
                category.getFullPath(),
                productCount,
                responseFactory.toMediaManifest(category.getImageUrl(), category.getName())
        );
    }

    private ProductVariant primaryVariant(Product product) {
        if (product == null || product.getVariants() == null || product.getVariants().isEmpty()) {
            return null;
        }

        return product.getVariants().stream()
                .filter(Objects::nonNull)
                .min(Comparator.comparing(
                        variant -> variant.getPrice() != null ? variant.getPrice().getAmount() : Long.MAX_VALUE
                ))
                .orElse(null);
    }

    private List<CatalogController.CategorySummary> categorySummaries(Product product) {
        if (product == null || product.getCategories() == null) {
            return List.of();
        }

        return product.getCategories().stream()
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(category -> Optional.ofNullable(category.getFullPath()).orElse(category.getSlug())))
                .map(category -> new CatalogController.CategorySummary(
                        category.getId(),
                        category.getName(),
                        category.getSlug(),
                        category.getFullPath()
                ))
                .toList();
    }

    private String firstCategorySlug(Product product) {
        return categorySummaries(product).stream()
                .findFirst()
                .map(CatalogController.CategorySummary::getSlug)
                .orElse(null);
    }

    private int totalStock(Product product) {
        if (product == null || product.getVariants() == null) {
            return 0;
        }

        return product.getVariants().stream()
                .filter(Objects::nonNull)
                .mapToInt(ProductVariant::getStockQuantity)
                .sum();
    }

    private List<String> parseKeys(String rawKeys) {
        if (!StringUtils.hasText(rawKeys)) {
            return List.of();
        }

        List<String> keys = new ArrayList<>();
        for (String token : rawKeys.split(",")) {
            String normalized = token.trim();
            if (StringUtils.hasText(normalized)) {
                keys.add(normalized);
            }
        }
        return keys;
    }

    private int clampLimit(int limit) {
        if (limit <= 0) {
            return 0;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    private String summarize(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String compact = value
                .replaceAll("<[^>]+>", " ")
                .replaceAll("\\s+", " ")
                .trim();
        return compact.length() <= 220 ? compact : compact.substring(0, 217).trim() + "...";
    }

    private String productKey(Product product) {
        return product.getSlug() != null ? product.getSlug() : String.valueOf(product.getId());
    }

    private String categoryKey(Category category) {
        return category.getSlug() != null ? category.getSlug() : String.valueOf(category.getId());
    }

    public record StorefrontCardsResponse(
            List<ProductCardResponse> products,
            List<CategoryCardResponse> categories,
            boolean compact
    ) {
    }

    public record ProductCardResponse(
            UUID id,
            String slug,
            String name,
            String description,
            String category,
            List<CatalogController.CategorySummary> categories,
            String brand,
            Money price,
            Money oldPrice,
            boolean onSale,
            Integer discountPercent,
            int stock,
            List<CatalogController.ImageResponse> images,
            MediaModels.MediaManifest primaryMedia
    ) {
    }

    public record CategoryCardResponse(
            UUID id,
            String name,
            String slug,
            String description,
            String imageUrl,
            UUID parentId,
            int position,
            boolean isActive,
            String fullPath,
            int productCount,
            MediaModels.MediaManifest media
    ) {
    }
}
