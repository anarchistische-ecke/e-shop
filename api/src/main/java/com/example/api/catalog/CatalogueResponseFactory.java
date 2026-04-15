package com.example.api.catalog;

import com.example.api.content.CatalogueContentModels;
import com.example.catalog.domain.Category;
import com.example.catalog.domain.Product;
import com.example.catalog.domain.ProductImage;
import com.example.catalog.domain.ProductVariant;
import com.example.catalog.service.CatalogService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class CatalogueResponseFactory {

    private final CatalogService catalogService;
    private final ObjectMapper objectMapper;

    public CatalogueResponseFactory(CatalogService catalogService, ObjectMapper objectMapper) {
        this.catalogService = catalogService;
        this.objectMapper = objectMapper;
    }

    public CatalogController.ProductResponse toProductResponse(Product product) {
        return toProductResponse(product, null);
    }

    public CatalogController.ProductResponse toProductResponse(Product product, CatalogueContentModels.CataloguePresentation presentation) {
        CatalogController.ProductResponse response = new CatalogController.ProductResponse();
        response.setId(product.getId());
        response.setName(product.getName());
        response.setDescription(product.getDescription());
        response.setSlug(product.getSlug());
        response.setSpecifications(deserializeSpecifications(product.getSpecifications()));
        List<CatalogController.CategorySummary> categories = mapCategories(product.getCategories());
        response.setCategories(categories);
        response.setCategory(categories.isEmpty() ? null : categories.getFirst().getSlug());
        response.setBrand(product.getBrand() != null ? product.getBrand().getSlug() : null);
        response.setIsActive(product.isIsActive());
        List<ProductImage> images = catalogService.getProductImages(product.getId());
        response.setImages(images != null
                ? images.stream().map(this::toImageResponse).collect(Collectors.toList())
                : List.of());
        response.setCreatedAt(product.getCreatedAt());
        response.setUpdatedAt(product.getUpdatedAt());
        response.setPresentation(presentation);
        if (product.getVariants() != null) {
            response.setVariants(product.getVariants().stream()
                    .map(this::toVariantResponse)
                    .toList());
        } else {
            response.setVariants(List.of());
        }
        return response;
    }

    public CategoryController.CategoryResponse toCategoryResponse(Category category) {
        return toCategoryResponse(category, null);
    }

    public CategoryController.CategoryResponse toCategoryResponse(
            Category category,
            CatalogueContentModels.CataloguePresentation presentation
    ) {
        UUID parentId = category.getParent() != null ? category.getParent().getId() : null;
        return new CategoryController.CategoryResponse(
                category.getId(),
                category.getName(),
                category.getSlug(),
                category.getDescription(),
                category.getImageUrl(),
                parentId,
                category.getPosition(),
                category.isIsActive(),
                category.getFullPath(),
                presentation
        );
    }

    public CatalogController.VariantResponse toVariantResponse(ProductVariant variant) {
        CatalogController.VariantResponse response = new CatalogController.VariantResponse();
        response.setId(variant.getId());
        response.setSku(variant.getSku());
        response.setName(variant.getName());
        response.setPrice(variant.getPrice());
        response.setStock(variant.getStockQuantity());
        response.setWeightGrossG(variant.getWeightGrossG());
        response.setLengthMm(variant.getLengthMm());
        response.setWidthMm(variant.getWidthMm());
        response.setHeightMm(variant.getHeightMm());
        return response;
    }

    public CatalogController.ImageResponse toImageResponse(ProductImage image) {
        CatalogController.ImageResponse response = new CatalogController.ImageResponse();
        response.setId(image.getId());
        response.setUrl(image.getUrl());
        response.setPosition(image.getPosition());
        response.setVariantId(image.getVariant() != null ? image.getVariant().getId() : null);
        return response;
    }

    public Set<Category> resolveCategories(CatalogController.ProductRequest request) {
        Set<Category> categories = new HashSet<>();
        if (request == null) {
            return categories;
        }
        List<String> refs = request.getCategories();
        if (refs != null) {
            refs.forEach(ref -> resolveCategoryRef(ref).ifPresent(categories::add));
            return categories;
        }
        if (request.getCategory() != null) {
            resolveCategoryRef(request.getCategory()).ifPresent(categories::add);
        }
        return categories;
    }

    public String serializeSpecifications(List<CatalogController.SpecificationSection> sections) {
        if (sections == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(sections);
        } catch (Exception error) {
            throw new IllegalArgumentException("Invalid specifications payload");
        }
    }

    public List<CatalogController.SpecificationSection> deserializeSpecifications(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(raw, new TypeReference<List<CatalogController.SpecificationSection>>() {
            });
        } catch (Exception error) {
            return List.of();
        }
    }

    private List<CatalogController.CategorySummary> mapCategories(Set<Category> categories) {
        if (categories == null || categories.isEmpty()) {
            return List.of();
        }
        return categories.stream()
                .filter(cat -> cat != null)
                .sorted(Comparator.comparing(cat -> Optional.ofNullable(cat.getFullPath()).orElse(cat.getSlug())))
                .map(cat -> new CatalogController.CategorySummary(cat.getId(), cat.getName(), cat.getSlug(), cat.getFullPath()))
                .toList();
    }

    private Optional<Category> resolveCategoryRef(String reference) {
        if (reference == null || reference.isBlank()) {
            return Optional.empty();
        }
        Optional<Category> bySlug = catalogService.getBySlug(reference);
        if (bySlug.isPresent()) {
            return bySlug;
        }
        try {
            return catalogService.getByCategoryId(UUID.fromString(reference));
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }
}
