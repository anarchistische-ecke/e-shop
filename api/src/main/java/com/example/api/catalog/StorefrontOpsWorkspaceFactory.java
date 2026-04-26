package com.example.api.catalog;

import com.example.api.content.CatalogueContentModels;
import com.example.catalog.domain.Brand;
import com.example.catalog.domain.Category;
import com.example.catalog.domain.Product;
import com.example.catalog.domain.ProductImage;
import com.example.catalog.domain.ProductVariant;
import com.example.catalog.service.CatalogService;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class StorefrontOpsWorkspaceFactory {

    private final CatalogService catalogService;
    private final CatalogueResponseFactory responseFactory;

    public StorefrontOpsWorkspaceFactory(
            CatalogService catalogService,
            CatalogueResponseFactory responseFactory
    ) {
        this.catalogService = catalogService;
        this.responseFactory = responseFactory;
    }

    public List<StorefrontOpsWorkspaceModels.BrandOption> toBrandOptions(List<Brand> brands) {
        if (brands == null || brands.isEmpty()) {
            return List.of();
        }
        return brands.stream()
                .filter(brand -> brand != null)
                .sorted(Comparator.comparing(Brand::getName, String.CASE_INSENSITIVE_ORDER))
                .map(StorefrontOpsWorkspaceModels.BrandOption::fromBrand)
                .toList();
    }

    public List<StorefrontOpsWorkspaceModels.CategoryOption> toCategoryOptions(List<Category> categories) {
        if (categories == null || categories.isEmpty()) {
            return List.of();
        }
        return categories.stream()
                .filter(category -> category != null)
                .sorted(Comparator.comparing(category -> Optional.ofNullable(category.getFullPath()).orElse(category.getSlug()), String.CASE_INSENSITIVE_ORDER))
                .map(this::toCategoryOption)
                .toList();
    }

    public StorefrontOpsWorkspaceModels.ProductSummary toProductSummary(
            Product product,
            CatalogueContentModels.CatalogueOverlay overlay
    ) {
        List<ProductImage> images = catalogService.getProductImages(product.getId());
        return new StorefrontOpsWorkspaceModels.ProductSummary(
                product.getId(),
                product.getName(),
                product.getSlug(),
                product.isIsActive(),
                StorefrontOpsWorkspaceModels.BrandOption.fromBrand(product.getBrand()),
                toCategoryOptionsFromSet(product.getCategories()),
                product.getVariants() != null ? product.getVariants().size() : 0,
                totalStock(product),
                images.stream()
                        .sorted(Comparator.comparing(ProductImage::getPosition).thenComparing(ProductImage::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                        .map(ProductImage::getUrl)
                        .filter(StringUtils::hasText)
                        .findFirst()
                        .orElse(null),
                product.getUpdatedAt(),
                toOverlayInfo(overlay)
        );
    }

    public StorefrontOpsWorkspaceModels.ProductDetail toProductDetail(
            Product product,
            CatalogueContentModels.CatalogueOverlay overlay
    ) {
        List<ProductImage> images = catalogService.getProductImages(product.getId());
        List<StorefrontOpsWorkspaceModels.VariantSummary> variants = product.getVariants() == null
                ? List.of()
                : product.getVariants().stream()
                .sorted(Comparator.comparing(ProductVariant::getName, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(ProductVariant::getSku, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                .map(this::toVariantSummary)
                .toList();
        return new StorefrontOpsWorkspaceModels.ProductDetail(
                product.getId(),
                product.getName(),
                product.getSlug(),
                product.getDescription(),
                product.isIsActive(),
                StorefrontOpsWorkspaceModels.BrandOption.fromBrand(product.getBrand()),
                toCategoryOptionsFromSet(product.getCategories()),
                responseFactory.deserializeSpecifications(product.getSpecifications()),
                images.stream()
                        .sorted(Comparator.comparing(ProductImage::getPosition).thenComparing(ProductImage::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                        .map(this::toImageSummary)
                        .toList(),
                variants,
                toOverlayInfo(overlay),
                product.getCreatedAt(),
                product.getUpdatedAt()
        );
    }

    public StorefrontOpsWorkspaceModels.CategorySummary toCategorySummary(
            Category category,
            CatalogueContentModels.CatalogueOverlay overlay
    ) {
        return new StorefrontOpsWorkspaceModels.CategorySummary(
                category.getId(),
                category.getName(),
                category.getSlug(),
                category.getFullPath(),
                category.getParent() != null ? category.getParent().getId() : null,
                calculateDepth(category),
                category.getPosition(),
                category.isIsActive(),
                category.getImageUrl(),
                toOverlayInfo(overlay)
        );
    }

    public StorefrontOpsWorkspaceModels.CategoryDetail toCategoryDetail(
            Category category,
            CatalogueContentModels.CatalogueOverlay overlay
    ) {
        return new StorefrontOpsWorkspaceModels.CategoryDetail(
                category.getId(),
                category.getName(),
                category.getSlug(),
                category.getDescription(),
                category.getImageUrl(),
                category.getParent() != null ? category.getParent().getId() : null,
                calculateDepth(category),
                category.getPosition(),
                category.isIsActive(),
                category.getFullPath(),
                toOverlayInfo(overlay)
        );
    }

    public StorefrontOpsWorkspaceModels.BrandSummary toBrandSummary(Brand brand, long productCount) {
        return new StorefrontOpsWorkspaceModels.BrandSummary(
                brand.getId(),
                brand.getName(),
                brand.getSlug(),
                brand.getDescription(),
                productCount
        );
    }

    public StorefrontOpsWorkspaceModels.BrandDetail toBrandDetail(Brand brand, long productCount) {
        return new StorefrontOpsWorkspaceModels.BrandDetail(
                brand.getId(),
                brand.getName(),
                brand.getSlug(),
                brand.getDescription(),
                productCount
        );
    }

    public StorefrontOpsWorkspaceModels.InventoryRow toInventoryRow(Product product, ProductVariant variant) {
        return new StorefrontOpsWorkspaceModels.InventoryRow(
                variant.getId(),
                product.getId(),
                product.getName(),
                product.getSlug(),
                product.isIsActive(),
                StorefrontOpsWorkspaceModels.BrandOption.fromBrand(product.getBrand()),
                toCategoryOptionsFromSet(product.getCategories()),
                variant.getName(),
                variant.getSku(),
                variant.getPrice(),
                variant.getStockQuantity(),
                product.getUpdatedAt()
        );
    }

    private List<StorefrontOpsWorkspaceModels.CategoryOption> toCategoryOptionsFromSet(Set<Category> categories) {
        if (categories == null || categories.isEmpty()) {
            return List.of();
        }
        return categories.stream()
                .filter(category -> category != null)
                .sorted(Comparator.comparing(category -> Optional.ofNullable(category.getFullPath()).orElse(category.getSlug()), String.CASE_INSENSITIVE_ORDER))
                .map(this::toCategoryOption)
                .toList();
    }

    private StorefrontOpsWorkspaceModels.CategoryOption toCategoryOption(Category category) {
        return new StorefrontOpsWorkspaceModels.CategoryOption(
                category.getId(),
                category.getName(),
                category.getSlug(),
                category.getFullPath(),
                category.getParent() != null ? category.getParent().getId() : null,
                calculateDepth(category)
        );
    }

    private StorefrontOpsWorkspaceModels.ProductImageSummary toImageSummary(ProductImage image) {
        return new StorefrontOpsWorkspaceModels.ProductImageSummary(
                image.getId(),
                image.getUrl(),
                image.getPosition(),
                image.getVariant() != null ? image.getVariant().getId() : null
        );
    }

    private StorefrontOpsWorkspaceModels.VariantSummary toVariantSummary(ProductVariant variant) {
        return new StorefrontOpsWorkspaceModels.VariantSummary(
                variant.getId(),
                variant.getSku(),
                variant.getName(),
                variant.getPrice(),
                variant.getStockQuantity(),
                variant.getWeightGrossG(),
                variant.getLengthMm(),
                variant.getWidthMm(),
                variant.getHeightMm()
        );
    }

    private StorefrontOpsWorkspaceModels.OverlayWorkspaceInfo toOverlayInfo(CatalogueContentModels.CatalogueOverlay overlay) {
        if (overlay == null) {
            return null;
        }
        String collection = "category".equalsIgnoreCase(overlay.entityKind()) ? "category_overlay" : "product_overlay";
        String keyField = "category".equalsIgnoreCase(overlay.entityKind()) ? "category_key" : "product_key";
        return new StorefrontOpsWorkspaceModels.OverlayWorkspaceInfo(
                overlay.itemId(),
                collection,
                keyField,
                overlay.key(),
                overlay.status(),
                overlay.publishedAt(),
                overlay.itemId() != null,
                overlay.marketingTitle(),
                overlay.badgeText(),
                overlay.linkedCollectionKeys() != null ? overlay.linkedCollectionKeys() : List.of()
        );
    }

    private int totalStock(Product product) {
        if (product.getVariants() == null || product.getVariants().isEmpty()) {
            return 0;
        }
        return product.getVariants().stream()
                .mapToInt(ProductVariant::getStockQuantity)
                .sum();
    }

    private int calculateDepth(Category category) {
        String fullPath = StringUtils.hasText(category.getFullPath()) ? category.getFullPath() : category.getSlug();
        if (!StringUtils.hasText(fullPath)) {
            return 0;
        }
        return Math.max(0, fullPath.split("/").length - 1);
    }
}
