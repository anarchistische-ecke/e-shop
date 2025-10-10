package com.example.api.catalog;

import com.example.catalog.domain.Product;
import com.example.catalog.domain.ProductVariant;
import com.example.catalog.service.CatalogService;
import com.example.common.domain.Money;
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
        Product product = catalogService.createProduct(
                request.getName(), request.getDescription(), request.getSlug());
        return ResponseEntity.status(HttpStatus.CREATED).body(product);
    }

    @PostMapping("/{productId}/variants")
    public ResponseEntity<ProductVariant> addProductVariant(@PathVariable UUID productId,
                                                            @Valid @RequestBody VariantRequest request) {
        Money price = Money.of(request.getAmount(), request.getCurrency());
        ProductVariant variant = catalogService.addVariant(
                productId, request.getSku(), request.getName(), price, request.getStock());
        return ResponseEntity.status(HttpStatus.CREATED).body(variant);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Product> getProductById(@PathVariable UUID id) {
        Product product = catalogService.getProduct(id)
                .orElseThrow(() -> new IllegalArgumentException("Product not found: " + id));
        return ResponseEntity.ok(product);
    }

    @GetMapping
    public ResponseEntity<List<Product>> getAllProducts() {
        List<Product> products = catalogService.getAllProducts();
        return ResponseEntity.ok(products);
    }

    public static class ProductRequest {
        @NotBlank
        private String name;
        private String description;
        @NotBlank
        private String slug;
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public String getSlug() { return slug; }
        public void setSlug(String slug) { this.slug = slug; }
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
        public String getSku() { return sku; }
        public void setSku(String sku) { this.sku = sku; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public Long getAmount() { return amount; }
        public void setAmount(Long amount) { this.amount = amount; }
        public String getCurrency() { return currency; }
        public void setCurrency(String currency) { this.currency = currency; }
        public Integer getStock() { return stock; }
        public void setStock(Integer stock) { this.stock = stock; }
    }
}

