package com.example.api.catalog;

import com.example.admin.service.AdminActivityService;
import com.example.api.inventory.InventoryController;
import com.example.catalog.domain.Category;
import com.example.catalog.domain.Product;
import com.example.catalog.service.CatalogService;
import com.example.catalog.service.InventoryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/internal/directus/catalogue")
public class DirectusBridgeController {

    private final CatalogService catalogService;
    private final InventoryService inventoryService;
    private final ProductImageStorageService productImageStorageService;
    private final CategoryImageStorageService categoryImageStorageService;
    private final CatalogueResponseFactory responseFactory;
    private final CataloguePresentationService presentationService;
    private final DirectusBridgeSecurity bridgeSecurity;
    private final AdminActivityService adminActivityService;
    private final ObjectMapper objectMapper;

    public DirectusBridgeController(
            CatalogService catalogService,
            InventoryService inventoryService,
            ProductImageStorageService productImageStorageService,
            CategoryImageStorageService categoryImageStorageService,
            CatalogueResponseFactory responseFactory,
            CataloguePresentationService presentationService,
            DirectusBridgeSecurity bridgeSecurity,
            AdminActivityService adminActivityService,
            ObjectMapper objectMapper
    ) {
        this.catalogService = catalogService;
        this.inventoryService = inventoryService;
        this.productImageStorageService = productImageStorageService;
        this.categoryImageStorageService = categoryImageStorageService;
        this.responseFactory = responseFactory;
        this.presentationService = presentationService;
        this.bridgeSecurity = bridgeSecurity;
        this.adminActivityService = adminActivityService;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/products")
    public ResponseEntity<List<CatalogController.ProductResponse>> listProducts(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String brand,
            @RequestParam(defaultValue = "true") boolean includeInactive,
            HttpServletRequest request
    ) {
        DirectusBridgeSecurity.DirectusBridgePrincipal principal = authorize(request);
        List<Product> products = ((StringUtils.hasText(category) || StringUtils.hasText(brand))
                ? catalogService.getProducts(category, brand)
                : catalogService.getAllProducts()).stream()
                .filter(product -> includeInactive || product.isIsActive())
                .toList();
        var presentations = presentationService.buildPublishedProductPresentationResults(products);
        audit(principal, "catalogue.products.list", Map.of(
                "category", category,
                "brand", brand,
                "includeInactive", includeInactive,
                "count", products.size()
        ));
        return ResponseEntity.ok(products.stream()
                .map(product -> responseFactory.toProductResponse(
                        product,
                        presentations.getOrDefault(normalize(product.getSlug()), presentationService.buildPublishedProductPresentation(product)).presentation()
                ))
                .toList());
    }

    @GetMapping("/products/{idOrSlug}")
    public ResponseEntity<CatalogController.ProductResponse> getProduct(
            @PathVariable String idOrSlug,
            HttpServletRequest request
    ) {
        DirectusBridgeSecurity.DirectusBridgePrincipal principal = authorize(request);
        Product product = resolveProduct(idOrSlug);
        audit(principal, "catalogue.product.get", Map.of("product", product.getSlug()));
        return ResponseEntity.ok(
                responseFactory.toProductResponse(product, presentationService.buildPublishedProductPresentation(product).presentation())
        );
    }

    @PostMapping("/products")
    public ResponseEntity<CatalogController.ProductResponse> createProduct(
            @Valid @RequestBody CatalogController.ProductRequest requestBody,
            HttpServletRequest request
    ) {
        DirectusBridgeSecurity.DirectusBridgePrincipal principal = authorize(request);
        Product product = catalogService.createProduct(requestBody.getName(), requestBody.getDescription(), requestBody.getSlug());
        if (requestBody.getSpecifications() != null) {
            product.setSpecifications(responseFactory.serializeSpecifications(requestBody.getSpecifications()));
        }
        Set<Category> categories = responseFactory.resolveCategories(requestBody);
        if (!categories.isEmpty() || requestBody.getCategories() != null || requestBody.getCategory() != null) {
            product.setCategories(categories);
        }
        if (requestBody.getBrand() != null && !requestBody.getBrand().isBlank()) {
            catalogService.getByBrandSlug(requestBody.getBrand()).ifPresent(product::setBrand);
        }
        boolean categoriesProvided = requestBody.getCategories() != null || requestBody.getCategory() != null;
        product = catalogService.updateProduct(product.getId(), product, categoriesProvided, requestBody.getIsActive());
        audit(principal, "catalogue.product.create", Map.of("productId", product.getId(), "slug", product.getSlug()));
        return ResponseEntity.status(HttpStatus.CREATED).body(
                responseFactory.toProductResponse(product, presentationService.buildPublishedProductPresentation(product).presentation())
        );
    }

    @PutMapping("/products/{id}")
    public ResponseEntity<CatalogController.ProductResponse> updateProduct(
            @PathVariable UUID id,
            @Valid @RequestBody CatalogController.ProductRequest requestBody,
            HttpServletRequest request
    ) {
        DirectusBridgeSecurity.DirectusBridgePrincipal principal = authorize(request);
        Product updates = new Product(requestBody.getName(), requestBody.getDescription(), requestBody.getSlug());
        if (requestBody.getSpecifications() != null) {
            updates.setSpecifications(responseFactory.serializeSpecifications(requestBody.getSpecifications()));
        }
        if (requestBody.getCategories() != null || requestBody.getCategory() != null) {
            updates.setCategories(responseFactory.resolveCategories(requestBody));
        }
        if (requestBody.getBrand() != null && !requestBody.getBrand().isBlank()) {
            catalogService.getByBrandSlug(requestBody.getBrand()).ifPresent(updates::setBrand);
        }
        boolean categoriesProvided = requestBody.getCategories() != null || requestBody.getCategory() != null;
        Product updated = catalogService.updateProduct(id, updates, categoriesProvided, requestBody.getIsActive());
        audit(principal, "catalogue.product.update", Map.of("productId", updated.getId(), "slug", updated.getSlug()));
        return ResponseEntity.ok(
                responseFactory.toProductResponse(updated, presentationService.buildPublishedProductPresentation(updated).presentation())
        );
    }

    @DeleteMapping("/products/{id}")
    public ResponseEntity<Void> deleteProduct(@PathVariable UUID id, HttpServletRequest request) {
        DirectusBridgeSecurity.DirectusBridgePrincipal principal = authorize(request);
        catalogService.getProductImages(id).forEach(image -> productImageStorageService.delete(image.getObjectKey()));
        catalogService.deleteProduct(id);
        audit(principal, "catalogue.product.delete", Map.of("productId", id));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/products/{productId}/variants")
    public ResponseEntity<CatalogController.VariantResponse> addVariant(
            @PathVariable UUID productId,
            @Valid @RequestBody CatalogController.VariantRequest requestBody,
            HttpServletRequest request
    ) {
        DirectusBridgeSecurity.DirectusBridgePrincipal principal = authorize(request);
        var price = com.example.common.domain.Money.of(requestBody.getAmount(), requestBody.getCurrency());
        var variant = catalogService.addVariant(
                productId,
                requestBody.getSku(),
                requestBody.getName(),
                price,
                requestBody.getStock(),
                requestBody.getWeightGrossG(),
                requestBody.getLengthMm(),
                requestBody.getWidthMm(),
                requestBody.getHeightMm()
        );
        audit(principal, "catalogue.variant.create", Map.of("productId", productId, "variantId", variant.getId(), "sku", variant.getSku()));
        return ResponseEntity.status(HttpStatus.CREATED).body(responseFactory.toVariantResponse(variant));
    }

    @PutMapping("/products/{productId}/variants/{variantId}")
    public ResponseEntity<CatalogController.VariantResponse> updateVariant(
            @PathVariable UUID productId,
            @PathVariable UUID variantId,
            @Valid @RequestBody CatalogController.VariantUpdateRequest requestBody,
            HttpServletRequest request
    ) {
        DirectusBridgeSecurity.DirectusBridgePrincipal principal = authorize(request);
        var price = com.example.common.domain.Money.of(requestBody.getAmount(), requestBody.getCurrency());
        var updated = catalogService.updateVariant(
                productId,
                variantId,
                requestBody.getName(),
                price,
                requestBody.getStock(),
                requestBody.getWeightGrossG(),
                requestBody.getLengthMm(),
                requestBody.getWidthMm(),
                requestBody.getHeightMm()
        );
        audit(principal, "catalogue.variant.update", Map.of("productId", productId, "variantId", variantId));
        return ResponseEntity.ok(responseFactory.toVariantResponse(updated));
    }

    @PostMapping(value = "/products/{productId}/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<List<CatalogController.ImageResponse>> uploadProductImages(
            @PathVariable UUID productId,
            @RequestPart("files") List<MultipartFile> files,
            @RequestParam(value = "position", required = false) Integer position,
            @RequestParam(value = "variantId", required = false) UUID variantId,
            HttpServletRequest request
    ) {
        DirectusBridgeSecurity.DirectusBridgePrincipal principal = authorize(request);
        if (files == null || files.isEmpty()) {
            throw new IllegalArgumentException("No image files were uploaded");
        }
        catalogService.getProduct(productId).orElseThrow(() -> new IllegalArgumentException("Product not found: " + productId));
        int startPosition = position != null ? position : catalogService.getProductImages(productId).size();
        List<CatalogController.ImageResponse> responses = new ArrayList<>();
        int offset = 0;
        for (MultipartFile file : files) {
            var stored = productImageStorageService.upload(productId, file, startPosition + offset);
            var image = catalogService.addProductImage(productId, stored.url(), stored.objectKey(), stored.position(), variantId);
            responses.add(responseFactory.toImageResponse(image));
            offset++;
        }
        audit(principal, "catalogue.product.images.upload", Map.of("productId", productId, "count", responses.size()));
        return ResponseEntity.status(HttpStatus.CREATED).body(responses);
    }

    @PutMapping("/products/{productId}/images/{imageId}")
    public ResponseEntity<CatalogController.ImageResponse> updateProductImage(
            @PathVariable UUID productId,
            @PathVariable UUID imageId,
            @Valid @RequestBody CatalogController.ImageUpdateRequest requestBody,
            HttpServletRequest request
    ) {
        DirectusBridgeSecurity.DirectusBridgePrincipal principal = authorize(request);
        var updated = catalogService.updateProductImage(productId, imageId, requestBody.getVariantId(), requestBody.getPosition());
        audit(principal, "catalogue.product.image.update", Map.of("productId", productId, "imageId", imageId));
        return ResponseEntity.ok(responseFactory.toImageResponse(updated));
    }

    @DeleteMapping("/products/{productId}/images/{imageId}")
    public ResponseEntity<Void> deleteProductImage(
            @PathVariable UUID productId,
            @PathVariable UUID imageId,
            HttpServletRequest request
    ) {
        DirectusBridgeSecurity.DirectusBridgePrincipal principal = authorize(request);
        String objectKey = catalogService.removeProductImage(productId, imageId);
        productImageStorageService.delete(objectKey);
        audit(principal, "catalogue.product.image.delete", Map.of("productId", productId, "imageId", imageId));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/categories")
    public ResponseEntity<List<CategoryController.CategoryResponse>> listCategories(HttpServletRequest request) {
        DirectusBridgeSecurity.DirectusBridgePrincipal principal = authorize(request);
        List<Category> categories = catalogService.listAllInCategory();
        var presentations = presentationService.buildPublishedCategoryPresentationResults(categories);
        audit(principal, "catalogue.categories.list", Map.of("count", categories.size()));
        return ResponseEntity.ok(categories.stream()
                .map(category -> responseFactory.toCategoryResponse(
                        category,
                        presentations.getOrDefault(normalize(category.getSlug()), presentationService.buildPublishedCategoryPresentation(category)).presentation()
                ))
                .toList());
    }

    @GetMapping("/categories/{idOrSlug}")
    public ResponseEntity<CategoryController.CategoryResponse> getCategory(
            @PathVariable String idOrSlug,
            HttpServletRequest request
    ) {
        DirectusBridgeSecurity.DirectusBridgePrincipal principal = authorize(request);
        Category category = resolveCategory(idOrSlug);
        audit(principal, "catalogue.category.get", Map.of("category", category.getSlug()));
        return ResponseEntity.ok(
                responseFactory.toCategoryResponse(category, presentationService.buildPublishedCategoryPresentation(category).presentation())
        );
    }

    @PostMapping("/categories")
    public ResponseEntity<CategoryController.CategoryResponse> createCategory(
            @Valid @RequestBody CategoryController.CategoryRequest requestBody,
            HttpServletRequest request
    ) {
        DirectusBridgeSecurity.DirectusBridgePrincipal principal = authorize(request);
        Category parent = null;
        if (requestBody.getParentId() != null) {
            parent = catalogService.getByCategoryId(requestBody.getParentId())
                    .orElseThrow(() -> new IllegalArgumentException("Category not found: " + requestBody.getParentId()));
        }
        Category category = new Category(requestBody.getName(), requestBody.getDescription(), requestBody.getSlug(), parent);
        if (requestBody.getPosition() != null) {
            category.setPosition(requestBody.getPosition());
        }
        if (requestBody.getIsActive() != null) {
            category.setIsActive(requestBody.getIsActive());
        }
        if (requestBody.getImageUrl() != null) {
            category.setImageUrl(requestBody.getImageUrl());
        }
        Category created = catalogService.create(category);
        audit(principal, "catalogue.category.create", Map.of("categoryId", created.getId(), "slug", created.getSlug()));
        return ResponseEntity.status(HttpStatus.CREATED).body(
                responseFactory.toCategoryResponse(created, presentationService.buildPublishedCategoryPresentation(created).presentation())
        );
    }

    @PutMapping("/categories/{id}")
    public ResponseEntity<CategoryController.CategoryResponse> updateCategory(
            @PathVariable UUID id,
            @Valid @RequestBody CategoryController.CategoryRequest requestBody,
            HttpServletRequest request
    ) {
        DirectusBridgeSecurity.DirectusBridgePrincipal principal = authorize(request);
        Category parent = null;
        if (requestBody.getParentId() != null) {
            parent = catalogService.getByCategoryId(requestBody.getParentId())
                    .orElseThrow(() -> new IllegalArgumentException("Category not found: " + requestBody.getParentId()));
        }
        Category updates = new Category(requestBody.getName(), requestBody.getDescription(), requestBody.getSlug(), parent);
        if (requestBody.getPosition() != null) {
            updates.setPosition(requestBody.getPosition());
        }
        if (requestBody.getIsActive() != null) {
            updates.setIsActive(requestBody.getIsActive());
        }
        if (requestBody.getImageUrl() != null) {
            updates.setImageUrl(requestBody.getImageUrl());
        }
        Category updated = catalogService.update(id, updates);
        audit(principal, "catalogue.category.update", Map.of("categoryId", updated.getId(), "slug", updated.getSlug()));
        return ResponseEntity.ok(
                responseFactory.toCategoryResponse(updated, presentationService.buildPublishedCategoryPresentation(updated).presentation())
        );
    }

    @PostMapping(value = "/categories/{id}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<CategoryController.CategoryResponse> uploadCategoryImage(
            @PathVariable UUID id,
            @RequestPart("file") MultipartFile file,
            HttpServletRequest request
    ) {
        DirectusBridgeSecurity.DirectusBridgePrincipal principal = authorize(request);
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Category image file is required");
        }
        catalogService.getByCategoryId(id)
                .orElseThrow(() -> new IllegalArgumentException("Category not found: " + id));
        var stored = categoryImageStorageService.upload(id, file);
        Category updated = catalogService.updateCategoryImage(id, stored.url());
        audit(principal, "catalogue.category.image.upload", Map.of("categoryId", id));
        return ResponseEntity.ok(
                responseFactory.toCategoryResponse(updated, presentationService.buildPublishedCategoryPresentation(updated).presentation())
        );
    }

    @DeleteMapping("/categories/{id}")
    public ResponseEntity<Void> deleteCategory(@PathVariable UUID id, HttpServletRequest request) {
        DirectusBridgeSecurity.DirectusBridgePrincipal principal = authorize(request);
        catalogService.deleteCategory(id);
        audit(principal, "catalogue.category.delete", Map.of("categoryId", id));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/brands")
    public ResponseEntity<List<com.example.catalog.domain.Brand>> listBrands(HttpServletRequest request) {
        DirectusBridgeSecurity.DirectusBridgePrincipal principal = authorize(request);
        List<com.example.catalog.domain.Brand> brands = catalogService.listAllInBrand();
        audit(principal, "catalogue.brands.list", Map.of("count", brands.size()));
        return ResponseEntity.ok(brands);
    }

    @PostMapping("/brands")
    public ResponseEntity<com.example.catalog.domain.Brand> createBrand(
            @Valid @RequestBody BrandController.BrandRequest requestBody,
            HttpServletRequest request
    ) {
        DirectusBridgeSecurity.DirectusBridgePrincipal principal = authorize(request);
        var created = catalogService.create(new com.example.catalog.domain.Brand(
                requestBody.getName(),
                requestBody.getDescription(),
                requestBody.getSlug()
        ));
        audit(principal, "catalogue.brand.create", Map.of("brandId", created.getId(), "slug", created.getSlug()));
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/brands/{id}")
    public ResponseEntity<com.example.catalog.domain.Brand> updateBrand(
            @PathVariable UUID id,
            @Valid @RequestBody BrandController.BrandRequest requestBody,
            HttpServletRequest request
    ) {
        DirectusBridgeSecurity.DirectusBridgePrincipal principal = authorize(request);
        var updated = catalogService.update(id, new com.example.catalog.domain.Brand(
                requestBody.getName(),
                requestBody.getDescription(),
                requestBody.getSlug()
        ));
        audit(principal, "catalogue.brand.update", Map.of("brandId", updated.getId(), "slug", updated.getSlug()));
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/brands/{id}")
    public ResponseEntity<Void> deleteBrand(@PathVariable UUID id, HttpServletRequest request) {
        DirectusBridgeSecurity.DirectusBridgePrincipal principal = authorize(request);
        catalogService.deleteBrand(id);
        audit(principal, "catalogue.brand.delete", Map.of("brandId", id));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/inventory/adjust")
    public ResponseEntity<InventoryController.StockAdjustmentResponse> adjustInventory(
            @RequestParam("idempotencyKey") String idempotencyKey,
            @Valid @RequestBody InventoryController.StockAdjustmentRequest requestBody,
            HttpServletRequest request
    ) {
        DirectusBridgeSecurity.DirectusBridgePrincipal principal = authorize(request);
        var result = inventoryService.adjustStock(requestBody.getVariantId(), requestBody.getDelta(), idempotencyKey, requestBody.getReason());
        var response = new InventoryController.StockAdjustmentResponse(
                requestBody.getVariantId(),
                result.variant().getStockQuantity(),
                result.applied(),
                idempotencyKey
        );
        audit(principal, "catalogue.inventory.adjust", Map.of(
                "variantId", requestBody.getVariantId(),
                "delta", requestBody.getDelta(),
                "applied", result.applied()
        ));
        return result.applied()
                ? ResponseEntity.status(HttpStatus.CREATED).body(response)
                : ResponseEntity.ok(response);
    }

    private DirectusBridgeSecurity.DirectusBridgePrincipal authorize(HttpServletRequest request) {
        bridgeSecurity.authorize(request);
        return bridgeSecurity.principal(request);
    }

    private Product resolveProduct(String idOrSlug) {
        try {
            return catalogService.getProduct(UUID.fromString(idOrSlug))
                    .orElseThrow(() -> new IllegalArgumentException("Product not found: " + idOrSlug));
        } catch (IllegalArgumentException ignored) {
            return catalogService.getProductBySlug(idOrSlug)
                    .orElseThrow(() -> new IllegalArgumentException("Product not found: " + idOrSlug));
        }
    }

    private Category resolveCategory(String idOrSlug) {
        try {
            return catalogService.getByCategoryId(UUID.fromString(idOrSlug))
                    .orElseThrow(() -> new IllegalArgumentException("Category not found: " + idOrSlug));
        } catch (IllegalArgumentException ignored) {
            return catalogService.getBySlug(idOrSlug)
                    .orElseThrow(() -> new IllegalArgumentException("Category not found: " + idOrSlug));
        }
    }

    private void audit(DirectusBridgeSecurity.DirectusBridgePrincipal principal, String action, Map<String, Object> details) {
        try {
            adminActivityService.record(principal.actor(), action, objectMapper.writeValueAsString(enrichDetails(principal, details)));
        } catch (Exception error) {
            adminActivityService.record(principal.actor(), action, String.valueOf(enrichDetails(principal, details)));
        }
    }

    private Map<String, Object> enrichDetails(DirectusBridgeSecurity.DirectusBridgePrincipal principal, Map<String, Object> details) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("directusUserId", principal.userId());
        payload.put("directusEmail", principal.email());
        payload.put("directusPrimaryRole", principal.primaryRole());
        payload.put("directusRoles", principal.roles());
        if (details != null) {
            payload.putAll(details);
        }
        return payload;
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim().toLowerCase() : null;
    }
}
