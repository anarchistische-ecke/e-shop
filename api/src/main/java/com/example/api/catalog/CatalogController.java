package com.example.api.catalog;

import com.example.catalog.domain.Product;
import com.example.catalog.domain.ProductVariant;
import com.example.catalog.service.CatalogService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/products")
public class CatalogController {

    private final CatalogService catalogService;

    @Autowired
    public CatalogController(CatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @PostMapping
    public ResponseEntity<ProductResponse> createProduct(@Valid @RequestBody ProductRequest request) {
        // Create the product with basic fields
        Product product = catalogService.createProduct(request.getName(), request.getDescription(), request.getSlug());
        // If a category slug/ID is provided, resolve it and assign
        if (request.getCategory() != null && !request.getCategory().isBlank()) {
            catalogService.getBySlug(request.getCategory()).ifPresent(product::setCategory);
        }
        // If a brand slug/ID is provided, resolve it and assign
        if (request.getBrand() != null && !request.getBrand().isBlank()) {
            catalogService.getByBrandSlug(request.getBrand()).ifPresent(product::setBrand);
        }
        // Persist the associations
        product = catalogService.updateProduct(product.getId(), product);
        return ResponseEntity.status(HttpStatus.CREATED).body(mapProduct(product));
    }

    @PostMapping("/{productId}/variants")
    public ResponseEntity<VariantResponse> addProductVariant(@PathVariable UUID productId,
                                                             @Valid @RequestBody VariantRequest request) {
        var price = com.example.common.domain.Money.of(request.getAmount(), request.getCurrency());
        var variant = catalogService.addVariant(productId, request.getSku(), request.getName(), price, request.getStock());
        return ResponseEntity.status(HttpStatus.CREATED).body(mapVariant(variant));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProductResponse> getProductById(@PathVariable UUID id) {
        var product = catalogService.getProduct(id).orElseThrow(() -> new IllegalArgumentException("Product not found: " + id));
        return ResponseEntity.ok(mapProduct(product));
    }

    @GetMapping
    public ResponseEntity<List<ProductResponse>> getProducts(@RequestParam(required = false) String category,
                                                             @RequestParam(required = false) String brand) {
        List<Product> source;
        if ((category != null && !category.isBlank()) || (brand != null && !brand.isBlank())) {
            source = catalogService.getProducts(category, brand);
        } else {
            source = catalogService.getAllProducts();
        }
        var response = source.stream().map(this::mapProduct).collect(Collectors.toList());
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ProductResponse> updateProduct(@PathVariable UUID id,
                                                         @Valid @RequestBody ProductRequest request) {
        // Build the updates object
        Product updates = new Product(request.getName(), request.getDescription(), request.getSlug());
        // Resolve category and brand if provided
        if (request.getCategory() != null && !request.getCategory().isBlank()) {
            catalogService.getBySlug(request.getCategory()).ifPresent(updates::setCategory);
        }
        if (request.getBrand() != null && !request.getBrand().isBlank()) {
            catalogService.getByBrandSlug(request.getBrand()).ifPresent(updates::setBrand);
        }
        var updated = catalogService.updateProduct(id, updates);
        return ResponseEntity.ok(mapProduct(updated));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProduct(@PathVariable UUID id) {
        catalogService.deleteProduct(id);
        return ResponseEntity.noContent().build();
    }


    public static class ProductRequest {
        @NotBlank
        private String name;
        private String description;
        @NotBlank
        private String slug;
        private String category;
        private String brand;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public String getSlug() {
            return slug;
        }

        public void setSlug(String slug) {
            this.slug = slug;
        }

        public String getCategory() {
            return category;
        }

        public void setCategory(String category) {
            this.category = category;
        }

        public String getBrand() {
            return brand;
        }

        public void setBrand(String brand) {
            this.brand = brand;
        }
    }

    public static class VariantRequest {
        @NotBlank
        private String sku;
        @NotBlank
        private String name;
        @NotNull
        private Long amount;
        @NotBlank
        private String currency;
        @NotNull
        private Integer stock;

        public String getSku() {
            return sku;
        }

        public void setSku(String sku) {
            this.sku = sku;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public Long getAmount() {
            return amount;
        }

        public void setAmount(Long amount) {
            this.amount = amount;
        }

        public String getCurrency() {
            return currency;
        }

        public void setCurrency(String currency) {
            this.currency = currency;
        }

        public Integer getStock() {
            return stock;
        }

        public void setStock(Integer stock) {
            this.stock = stock;
        }
    }

    public static class ProductResponse {
        private UUID id;
        private String name;
        private String description;
        private String slug;
        private String category;
        private String brand;
        private OffsetDateTime createdAt;
        private OffsetDateTime updatedAt;
        private List<VariantResponse> variants;

        public UUID getId() {
            return id;
        }

        public void setId(UUID id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public String getSlug() {
            return slug;
        }

        public void setSlug(String slug) {
            this.slug = slug;
        }

        public String getCategory() {
            return category;
        }

        public void setCategory(String category) {
            this.category = category;
        }

        public String getBrand() {
            return brand;
        }

        public void setBrand(String brand) {
            this.brand = brand;
        }

        public OffsetDateTime getCreatedAt() {
            return createdAt;
        }

        public void setCreatedAt(OffsetDateTime createdAt) {
            this.createdAt = createdAt;
        }

        public OffsetDateTime getUpdatedAt() {
            return updatedAt;
        }

        public void setUpdatedAt(OffsetDateTime updatedAt) {
            this.updatedAt = updatedAt;
        }

        public List<VariantResponse> getVariants() {
            return variants;
        }

        public void setVariants(List<VariantResponse> variants) {
            this.variants = variants;
        }
    }

    public static class VariantResponse {
        private UUID id;
        private String sku;
        private String name;
        private com.example.common.domain.Money price;
        private int stock;

        public UUID getId() {
            return id;
        }

        public void setId(UUID id) {
            this.id = id;
        }

        public String getSku() {
            return sku;
        }

        public void setSku(String sku) {
            this.sku = sku;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public com.example.common.domain.Money getPrice() {
            return price;
        }

        public void setPrice(com.example.common.domain.Money price) {
            this.price = price;
        }

        public int getStock() {
            return stock;
        }

        public void setStock(int stock) {
            this.stock = stock;
        }
    }

    private ProductResponse mapProduct(Product product) {
        ProductResponse response = new ProductResponse();
        response.setId(product.getId());
        response.setName(product.getName());
        response.setDescription(product.getDescription());
        response.setSlug(product.getSlug());
        response.setCategory(product.getCategory() != null ? product.getCategory().getSlug() : null);
        response.setBrand(product.getBrand() != null ? product.getBrand().getSlug() : null);
        response.setCreatedAt(product.getCreatedAt());
        response.setUpdatedAt(product.getUpdatedAt());
        if (product.getVariants() != null) {
            response.setVariants(product.getVariants().stream().map(this::mapVariant).collect(Collectors.toList()));
        } else {
            response.setVariants(List.of());
        }
        return response;
    }

    private VariantResponse mapVariant(ProductVariant variant) {
        VariantResponse response = new VariantResponse();
        response.setId(variant.getId());
        response.setSku(variant.getSku());
        response.setName(variant.getName());
        response.setPrice(variant.getPrice());
        response.setStock(variant.getStockQuantity());
        return response;
    }
}
