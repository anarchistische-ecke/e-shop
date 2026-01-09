package com.example.api.catalog;

import com.example.catalog.domain.Category;
import com.example.catalog.domain.Product;
import com.example.catalog.domain.ProductImage;
import com.example.catalog.domain.ProductVariant;
import com.example.catalog.service.CatalogService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/products")
public class CatalogController {

    private final CatalogService catalogService;
    private final ProductImageStorageService imageStorageService;
    private final ObjectMapper objectMapper;

    @Autowired
    public CatalogController(CatalogService catalogService, ProductImageStorageService imageStorageService, ObjectMapper objectMapper) {
        this.catalogService = catalogService;
        this.imageStorageService = imageStorageService;
        this.objectMapper = objectMapper;
    }

    @PostMapping
    public ResponseEntity<ProductResponse> createProduct(@Valid @RequestBody ProductRequest request) {
        // Create the product with basic fields
        Product product = catalogService.createProduct(request.getName(), request.getDescription(), request.getSlug());
        if (request.getSpecifications() != null) {
            product.setSpecifications(serializeSpecifications(request.getSpecifications()));
        }
        // If categories are provided, resolve them and assign
        Set<Category> categories = resolveCategories(request);
        if (!categories.isEmpty() || request.getCategories() != null || request.getCategory() != null) {
            product.setCategories(categories);
        }
        // If a brand slug/ID is provided, resolve it and assign
        if (request.getBrand() != null && !request.getBrand().isBlank()) {
            catalogService.getByBrandSlug(request.getBrand()).ifPresent(product::setBrand);
        }
        // Persist the associations
        boolean categoriesProvided = request.getCategories() != null || request.getCategory() != null;
        product = catalogService.updateProduct(product.getId(), product, categoriesProvided);
        return ResponseEntity.status(HttpStatus.CREATED).body(mapProduct(product));
    }

    @PostMapping("/{productId}/variants")
    public ResponseEntity<VariantResponse> addProductVariant(@PathVariable UUID productId,
                                                             @Valid @RequestBody VariantRequest request) {
        var price = com.example.common.domain.Money.of(request.getAmount(), request.getCurrency());
        var variant = catalogService.addVariant(productId, request.getSku(), request.getName(), price, request.getStock());
        return ResponseEntity.status(HttpStatus.CREATED).body(mapVariant(variant));
    }

    @PutMapping("/{productId}/variants/{variantId}")
    public ResponseEntity<VariantResponse> updateProductVariant(@PathVariable UUID productId,
                                                                @PathVariable UUID variantId,
                                                                @Valid @RequestBody VariantUpdateRequest request) {
        var price = com.example.common.domain.Money.of(request.getAmount(), request.getCurrency());
        var updated = catalogService.updateVariant(productId, variantId, request.getName(), price, request.getStock());
        return ResponseEntity.ok(mapVariant(updated));
    }

    @PostMapping(value = "/{productId}/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<List<ImageResponse>> uploadProductImages(@PathVariable UUID productId,
                                                                   @RequestPart("files") List<MultipartFile> files,
                                                                   @RequestParam(value = "position", required = false) Integer position,
                                                                   @RequestParam(value = "variantId", required = false) UUID variantId) {
        if (files == null || files.isEmpty()) {
            throw new IllegalArgumentException("Не переданы файлы изображений");
        }
        catalogService.getProduct(productId).orElseThrow(() -> new IllegalArgumentException("Product not found: " + productId));
        int startPosition = position != null ? position : catalogService.getProductImages(productId).size();
        List<ImageResponse> responses = new ArrayList<>();
        int offset = 0;
        for (MultipartFile file : files) {
            var stored = imageStorageService.upload(productId, file, startPosition + offset);
            var image = catalogService.addProductImage(productId, stored.url(), stored.objectKey(), stored.position(), variantId);
            responses.add(mapImage(image));
            offset++;
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(responses);
    }

    @GetMapping("/{productId}/images")
    public ResponseEntity<List<ImageResponse>> listProductImages(@PathVariable UUID productId) {
        var images = catalogService.getProductImages(productId).stream()
                .map(this::mapImage)
                .collect(Collectors.toList());
        return ResponseEntity.ok(images);
    }

    @DeleteMapping("/{productId}/images/{imageId}")
    public ResponseEntity<Void> deleteProductImage(@PathVariable UUID productId, @PathVariable UUID imageId) {
        String objectKey = catalogService.removeProductImage(productId, imageId);
        imageStorageService.delete(objectKey);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{productId}/images/{imageId}")
    public ResponseEntity<ImageResponse> updateProductImage(@PathVariable UUID productId,
                                                            @PathVariable UUID imageId,
                                                            @Valid @RequestBody ImageUpdateRequest request) {
        var updated = catalogService.updateProductImage(productId, imageId, request.getVariantId(), request.getPosition());
        return ResponseEntity.ok(mapImage(updated));
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
        if (request.getSpecifications() != null) {
            updates.setSpecifications(serializeSpecifications(request.getSpecifications()));
        }
        // Resolve categories and brand if provided
        if (request.getCategories() != null || request.getCategory() != null) {
            updates.setCategories(resolveCategories(request));
        }
        if (request.getBrand() != null && !request.getBrand().isBlank()) {
            catalogService.getByBrandSlug(request.getBrand()).ifPresent(updates::setBrand);
        }
        boolean categoriesProvided = request.getCategories() != null || request.getCategory() != null;
        var updated = catalogService.updateProduct(id, updates, categoriesProvided);
        return ResponseEntity.ok(mapProduct(updated));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProduct(@PathVariable UUID id) {
        var images = catalogService.getProductImages(id);
        images.forEach(img -> imageStorageService.delete(img.getObjectKey()));
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
        private List<String> categories;
        private String brand;
        private List<SpecificationSection> specifications;

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

        public List<String> getCategories() {
            return categories;
        }

        public void setCategories(List<String> categories) {
            this.categories = categories;
        }

        public String getBrand() {
            return brand;
        }

        public void setBrand(String brand) {
            this.brand = brand;
        }

        public List<SpecificationSection> getSpecifications() {
            return specifications;
        }

        public void setSpecifications(List<SpecificationSection> specifications) {
            this.specifications = specifications;
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

    public static class VariantUpdateRequest {
        @NotBlank
        private String name;
        @NotNull
        private Long amount;
        @NotBlank
        private String currency;
        @NotNull
        private Integer stock;

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
        private List<CategorySummary> categories;
        private String brand;
        private List<ImageResponse> images;
        private OffsetDateTime createdAt;
        private OffsetDateTime updatedAt;
        private List<VariantResponse> variants;
        private List<SpecificationSection> specifications;

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

        public List<CategorySummary> getCategories() {
            return categories;
        }

        public void setCategories(List<CategorySummary> categories) {
            this.categories = categories;
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

        public List<ImageResponse> getImages() {
            return images;
        }

        public void setImages(List<ImageResponse> images) {
            this.images = images;
        }

        public List<SpecificationSection> getSpecifications() {
            return specifications;
        }

        public void setSpecifications(List<SpecificationSection> specifications) {
            this.specifications = specifications;
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

    public static class ImageResponse {
        private UUID id;
        private String url;
        private int position;
        private UUID variantId;

        public UUID getId() {
            return id;
        }

        public void setId(UUID id) {
            this.id = id;
        }

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public int getPosition() {
            return position;
        }

        public void setPosition(int position) {
            this.position = position;
        }

        public UUID getVariantId() {
            return variantId;
        }

        public void setVariantId(UUID variantId) {
            this.variantId = variantId;
        }
    }

    public static class ImageUpdateRequest {
        private UUID variantId;
        private Integer position;

        public UUID getVariantId() {
            return variantId;
        }

        public void setVariantId(UUID variantId) {
            this.variantId = variantId;
        }

        public Integer getPosition() {
            return position;
        }

        public void setPosition(Integer position) {
            this.position = position;
        }
    }

    public static class SpecificationSection {
        private String title;
        private String description;
        private List<SpecificationItem> items;

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public List<SpecificationItem> getItems() {
            return items;
        }

        public void setItems(List<SpecificationItem> items) {
            this.items = items;
        }
    }

    public static class SpecificationItem {
        private String label;
        private String value;

        public String getLabel() {
            return label;
        }

        public void setLabel(String label) {
            this.label = label;
        }

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }
    }

    public static class CategorySummary {
        private UUID id;
        private String name;
        private String slug;
        private String fullPath;

        public CategorySummary(UUID id, String name, String slug, String fullPath) {
            this.id = id;
            this.name = name;
            this.slug = slug;
            this.fullPath = fullPath;
        }

        public UUID getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public String getSlug() {
            return slug;
        }

        public String getFullPath() {
            return fullPath;
        }
    }

    private ProductResponse mapProduct(Product product) {
        ProductResponse response = new ProductResponse();
        response.setId(product.getId());
        response.setName(product.getName());
        response.setDescription(product.getDescription());
        response.setSlug(product.getSlug());
        response.setSpecifications(deserializeSpecifications(product.getSpecifications()));
        List<CategorySummary> categories = mapCategories(product.getCategories());
        response.setCategories(categories);
        response.setCategory(categories.isEmpty() ? null : categories.get(0).getSlug());
        response.setBrand(product.getBrand() != null ? product.getBrand().getSlug() : null);
        var images = catalogService.getProductImages(product.getId());
        response.setImages(images != null
                ? images.stream().map(this::mapImage).collect(Collectors.toList())
                : List.of());
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

    private ImageResponse mapImage(ProductImage image) {
        ImageResponse response = new ImageResponse();
        response.setId(image.getId());
        response.setUrl(image.getUrl());
        response.setPosition(image.getPosition());
        response.setVariantId(image.getVariant() != null ? image.getVariant().getId() : null);
        return response;
    }

    private List<CategorySummary> mapCategories(Set<Category> categories) {
        if (categories == null || categories.isEmpty()) {
            return List.of();
        }
        return categories.stream()
                .filter(cat -> cat != null)
                .sorted(Comparator.comparing(cat -> Optional.ofNullable(cat.getFullPath()).orElse(cat.getSlug())))
                .map(cat -> new CategorySummary(cat.getId(), cat.getName(), cat.getSlug(), cat.getFullPath()))
                .collect(Collectors.toList());
    }

    private Set<Category> resolveCategories(ProductRequest request) {
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

    private String serializeSpecifications(List<SpecificationSection> sections) {
        if (sections == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(sections);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid specifications payload");
        }
    }

    private List<SpecificationSection> deserializeSpecifications(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(raw, new TypeReference<List<SpecificationSection>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }
}
