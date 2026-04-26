package com.example.api.catalog;

import com.example.catalog.domain.Brand;
import com.example.common.domain.Money;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class StorefrontOpsWorkspaceModels {

    private StorefrontOpsWorkspaceModels() {
    }

    public record BrandOption(
            UUID id,
            String name,
            String slug
    ) {
        public static BrandOption fromBrand(Brand brand) {
            if (brand == null) {
                return null;
            }
            return new BrandOption(brand.getId(), brand.getName(), brand.getSlug());
        }
    }

    public record CategoryOption(
            UUID id,
            String name,
            String slug,
            String fullPath,
            UUID parentId,
            int depth
    ) {
    }

    public record OverlayWorkspaceInfo(
            Integer itemId,
            String collection,
            String keyField,
            String key,
            String status,
            OffsetDateTime publishedAt,
            boolean exists,
            String marketingTitle,
            String badgeText,
            List<String> linkedCollectionKeys
    ) {
    }

    public record ProductImageSummary(
            UUID id,
            String url,
            int position,
            UUID variantId
    ) {
    }

    public record VariantSummary(
            UUID id,
            String sku,
            String name,
            Money price,
            int stock,
            Integer weightGrossG,
            Integer lengthMm,
            Integer widthMm,
            Integer heightMm
    ) {
    }

    public record ProductSummary(
            UUID id,
            String name,
            String slug,
            boolean isActive,
            BrandOption brand,
            List<CategoryOption> categories,
            int variantCount,
            int totalStock,
            String primaryImageUrl,
            OffsetDateTime updatedAt,
            OverlayWorkspaceInfo overlay
    ) {
    }

    public record ProductDetail(
            UUID id,
            String name,
            String slug,
            String description,
            boolean isActive,
            BrandOption brand,
            List<CategoryOption> categories,
            List<CatalogController.SpecificationSection> specifications,
            List<ProductImageSummary> images,
            List<VariantSummary> variants,
            OverlayWorkspaceInfo overlay,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {
    }

    public record ProductWorkspaceList(
            List<ProductSummary> items,
            List<BrandOption> brandOptions,
            List<CategoryOption> categoryOptions,
            boolean overlayReadFailed
    ) {
    }

    public record ProductWorkspaceDetail(
            ProductDetail item,
            List<BrandOption> brandOptions,
            List<CategoryOption> categoryOptions,
            boolean overlayReadFailed
    ) {
    }

    public record CategorySummary(
            UUID id,
            String name,
            String slug,
            String fullPath,
            UUID parentId,
            int depth,
            int position,
            boolean isActive,
            String imageUrl,
            OverlayWorkspaceInfo overlay
    ) {
    }

    public record CategoryDetail(
            UUID id,
            String name,
            String slug,
            String description,
            String imageUrl,
            UUID parentId,
            int depth,
            int position,
            boolean isActive,
            String fullPath,
            OverlayWorkspaceInfo overlay
    ) {
    }

    public record CategoryWorkspaceList(
            List<CategorySummary> items,
            List<CategoryOption> parentOptions,
            boolean overlayReadFailed
    ) {
    }

    public record CategoryWorkspaceDetail(
            CategoryDetail item,
            List<CategoryOption> parentOptions,
            boolean overlayReadFailed
    ) {
    }

    public record BrandSummary(
            UUID id,
            String name,
            String slug,
            String description,
            long productCount
    ) {
    }

    public record BrandDetail(
            UUID id,
            String name,
            String slug,
            String description,
            long productCount
    ) {
    }

    public record BrandWorkspaceList(
            List<BrandSummary> items
    ) {
    }

    public record BrandWorkspaceDetail(
            BrandDetail item
    ) {
    }

    public record InventoryRow(
            UUID variantId,
            UUID productId,
            String productName,
            String productSlug,
            boolean productIsActive,
            BrandOption brand,
            List<CategoryOption> categories,
            String variantName,
            String sku,
            Money price,
            int stock,
            OffsetDateTime updatedAt
    ) {
    }

    public record InventoryWorkspaceList(
            List<InventoryRow> items
    ) {
    }

    public record NavigationSummary(
            int productCount,
            int categoryCount,
            int brandCount,
            int inventoryCount
    ) {
    }
}
