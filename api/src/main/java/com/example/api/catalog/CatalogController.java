package com.example.api.catalog;

import com.example.catalog.domain.Product;
import com.example.catalog.service.CatalogService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/products")
public class CatalogController {

    private final CatalogService catalogService;

    @Autowired
    public CatalogController(CatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @PostMapping
    public ResponseEntity<Product> createProduct(@Valid @RequestBody ProductRequest request) {
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
        return ResponseEntity.status(HttpStatus.CREATED).body(product);
    }

    @PostMapping("/{productId}/variants")
    public ResponseEntity<?> addProductVariant(@PathVariable UUID productId,
                                               @Valid @RequestBody VariantRequest request) {
        var price = com.example.common.domain.Money.of(request.getAmount(), request.getCurrency());
        var variant = catalogService.addVariant(productId, request.getSku(), request.getName(), price, request.getStock());
        return ResponseEntity.status(HttpStatus.CREATED).body(variant);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Product> getProductById(@PathVariable UUID id) {
        var product = catalogService.getProduct(id).orElseThrow(() -> new IllegalArgumentException("Product not found: " + id));
        return ResponseEntity.ok(product);
    }

    @GetMapping
    public ResponseEntity<List<Product>> getProducts(@RequestParam(required = false) String category,
                                                     @RequestParam(required = false) String brand) {
        if ((category != null && !category.isBlank()) || (brand != null && !brand.isBlank())) {
            var filtered = catalogService.getProducts(category, brand);
            return ResponseEntity.ok(filtered);
        }
        return ResponseEntity.ok(catalogService.getAllProducts());
    }

    @PutMapping("/{id}")
    public ResponseEntity<Product> updateProduct(@PathVariable UUID id,
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
        return ResponseEntity.ok(updated);
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
}