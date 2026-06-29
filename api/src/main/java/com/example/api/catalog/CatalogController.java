package com.example.api.catalog;

import com.example.api.content.CatalogueContentModels;
import com.example.catalog.domain.Category;
import com.example.catalog.domain.Product;
import com.example.catalog.service.CatalogService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.OffsetDateTime;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/products")
public class CatalogController {

    private static final int DEFAULT_PAGE_SIZE = 48;
    private static final int MAX_PAGE_SIZE = 96;

    private final CatalogService catalogService;
    private final ProductImageStorageService imageStorageService;
    private final CatalogueResponseFactory responseFactory;
    private final CataloguePresentationService presentationService;

    @Autowired
    public CatalogController(
            CatalogService catalogService,
            ProductImageStorageService imageStorageService,
            CatalogueResponseFactory responseFactory,
            CataloguePresentationService presentationService
    ) {
        this.catalogService = catalogService;
        this.imageStorageService = imageStorageService;
        this.responseFactory = responseFactory;
        this.presentationService = presentationService;
    }

    @PostMapping
    public ResponseEntity<ProductResponse> createProduct(@Valid @RequestBody ProductRequest request) {
        // Create the product with basic fields
        Product product = catalogService.createProduct(request.getName(), request.getDescription(), request.getSlug());
        if (request.getSpecifications() != null) {
            product.setSpecifications(responseFactory.serializeSpecifications(request.getSpecifications()));
        }
        // If categories are provided, resolve them and assign
        Set<Category> categories = responseFactory.resolveCategories(request);
        if (!categories.isEmpty() || request.getCategories() != null || request.getCategory() != null) {
            product.setCategories(categories);
        }
        // If a brand slug/ID is provided, resolve it and assign
        if (request.getBrand() != null && !request.getBrand().isBlank()) {
            catalogService.getByBrandSlug(request.getBrand()).ifPresent(product::setBrand);
        }
        // Persist the associations
        boolean categoriesProvided = request.getCategories() != null || request.getCategory() != null;
        product = catalogService.updateProduct(product.getId(), product, categoriesProvided, request.getIsActive());
        return ResponseEntity.status(HttpStatus.CREATED).body(
                responseFactory.toProductResponse(product, presentationService.buildPublishedProductPresentation(product).presentation())
        );
    }

    @PostMapping("/{productId}/variants")
    public ResponseEntity<VariantResponse> addProductVariant(@PathVariable UUID productId,
                                                             @Valid @RequestBody VariantRequest request) {
        var price = com.example.common.domain.Money.of(request.getAmount(), request.getCurrency());
        var variant = catalogService.addVariant(
                productId,
                request.getSku(),
                request.getName(),
                price,
                request.getStock(),
                request.getWeightGrossG(),
                request.getLengthMm(),
                request.getWidthMm(),
                request.getHeightMm(),
                request.getColorCode(),
                request.getColorLabel(),
                request.getColorHex(),
                request.getSizeCode(),
                request.getSizeLabel(),
                request.getSortOrder()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(responseFactory.toVariantResponse(variant));
    }

    @PutMapping("/{productId}/variants/{variantId}")
    public ResponseEntity<VariantResponse> updateProductVariant(@PathVariable UUID productId,
                                                                @PathVariable UUID variantId,
                                                                @Valid @RequestBody VariantUpdateRequest request) {
        var price = com.example.common.domain.Money.of(request.getAmount(), request.getCurrency());
        var updated = catalogService.updateVariant(
                productId,
                variantId,
                request.getName(),
                price,
                request.getStock(),
                request.getWeightGrossG(),
                request.getLengthMm(),
                request.getWidthMm(),
                request.getHeightMm(),
                request.getColorCode(),
                request.getColorLabel(),
                request.getColorHex(),
                request.getSizeCode(),
                request.getSizeLabel(),
                request.getSortOrder()
        );
        return ResponseEntity.ok(responseFactory.toVariantResponse(updated));
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
            responses.add(responseFactory.toImageResponse(image));
            offset++;
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(responses);
    }

    @GetMapping("/{productId}/images")
    public ResponseEntity<List<ImageResponse>> listProductImages(@PathVariable UUID productId) {
        var images = catalogService.getProductImages(productId).stream()
                .map(responseFactory::toImageResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(images);
    }

    @GetMapping("/images/{imageId}/preview")
    public ResponseEntity<byte[]> previewProductImage(@PathVariable UUID imageId) {
        var image = catalogService.getProductImage(imageId);
        var content = imageStorageService.download(image.getObjectKey());
        MediaType contentType = content.contentType() != null && !content.contentType().isBlank()
                ? MediaType.parseMediaType(content.contentType())
                : MediaType.APPLICATION_OCTET_STREAM;
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable())
                .contentType(contentType)
                .body(content.bytes());
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
        return ResponseEntity.ok(responseFactory.toImageResponse(updated));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProductResponse> getProductById(@PathVariable UUID id,
                                                          @RequestParam(defaultValue = "false") boolean includeInactive) {
        var product = catalogService.getProduct(id).orElseThrow(() -> new IllegalArgumentException("Product not found: " + id));
        if (!includeInactive && !product.isIsActive()) {
            throw new IllegalArgumentException("Product not found: " + id);
        }
        return ResponseEntity.ok(
                responseFactory.toProductResponse(product, presentationService.buildPublishedProductPresentation(product).presentation())
        );
    }

    @GetMapping
    public ResponseEntity<List<ProductResponse>> getProducts(@RequestParam(required = false) String category,
                                                             @RequestParam(required = false) String brand,
                                                             @RequestParam(defaultValue = "false") boolean includeInactive,
                                                             @RequestParam(defaultValue = "0") int page,
                                                             @RequestParam(defaultValue = "" + DEFAULT_PAGE_SIZE) int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, Math.min(size, MAX_PAGE_SIZE));
        PageRequest pageable = PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.ASC, "name"));
        Page<Product> source = catalogService.getProducts(category, brand, includeInactive, pageable);
        List<Product> products = source.getContent();
        var presentations = presentationService.buildPublishedProductPresentationResults(products);
        var response = products.stream()
                .map(product -> responseFactory.toProductResponse(
                        product,
                        presentationFor(product, presentations).presentation()
                ))
                .collect(Collectors.toList());
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header("X-Page", String.valueOf(source.getNumber()))
                .header("X-Page-Size", String.valueOf(source.getSize()))
                .header("X-Total-Count", String.valueOf(source.getTotalElements()))
                .header("X-Total-Pages", String.valueOf(source.getTotalPages()))
                .body(response);
    }

    private CataloguePresentationModels.OverlayMergeResult presentationFor(
            Product product,
            java.util.Map<String, CataloguePresentationModels.OverlayMergeResult> presentations
    ) {
        String key = product.getSlug() != null ? product.getSlug().trim().toLowerCase() : null;
        CataloguePresentationModels.OverlayMergeResult presentation = presentations.get(key);
        return presentation != null ? presentation : presentationService.buildPublishedProductPresentation(product);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ProductResponse> updateProduct(@PathVariable UUID id,
                                                         @Valid @RequestBody ProductRequest request) {
        // Build the updates object
        Product updates = new Product(request.getName(), request.getDescription(), request.getSlug());
        if (request.getSpecifications() != null) {
            updates.setSpecifications(responseFactory.serializeSpecifications(request.getSpecifications()));
        }
        // Resolve categories and brand if provided
        if (request.getCategories() != null || request.getCategory() != null) {
            updates.setCategories(responseFactory.resolveCategories(request));
        }
        if (request.getBrand() != null && !request.getBrand().isBlank()) {
            catalogService.getByBrandSlug(request.getBrand()).ifPresent(updates::setBrand);
        }
        boolean categoriesProvided = request.getCategories() != null || request.getCategory() != null;
        var updated = catalogService.updateProduct(id, updates, categoriesProvided, request.getIsActive());
        return ResponseEntity.ok(
                responseFactory.toProductResponse(updated, presentationService.buildPublishedProductPresentation(updated).presentation())
        );
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
        private Boolean isActive;
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

        public Boolean getIsActive() {
            return isActive;
        }

        public void setIsActive(Boolean isActive) {
            this.isActive = isActive;
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
        private Integer weightGrossG;
        private Integer lengthMm;
        private Integer widthMm;
        private Integer heightMm;
        private String colorCode;
        private String colorLabel;
        private String colorHex;
        private String sizeCode;
        private String sizeLabel;
        private Integer sortOrder;

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

        public Integer getWeightGrossG() {
            return weightGrossG;
        }

        public void setWeightGrossG(Integer weightGrossG) {
            this.weightGrossG = weightGrossG;
        }

        public Integer getLengthMm() {
            return lengthMm;
        }

        public void setLengthMm(Integer lengthMm) {
            this.lengthMm = lengthMm;
        }

        public Integer getWidthMm() {
            return widthMm;
        }

        public void setWidthMm(Integer widthMm) {
            this.widthMm = widthMm;
        }

        public Integer getHeightMm() {
            return heightMm;
        }

        public void setHeightMm(Integer heightMm) {
            this.heightMm = heightMm;
        }

        public String getColorCode() {
            return colorCode;
        }

        public void setColorCode(String colorCode) {
            this.colorCode = colorCode;
        }

        public String getColorLabel() {
            return colorLabel;
        }

        public void setColorLabel(String colorLabel) {
            this.colorLabel = colorLabel;
        }

        public String getColorHex() {
            return colorHex;
        }

        public void setColorHex(String colorHex) {
            this.colorHex = colorHex;
        }

        public String getSizeCode() {
            return sizeCode;
        }

        public void setSizeCode(String sizeCode) {
            this.sizeCode = sizeCode;
        }

        public String getSizeLabel() {
            return sizeLabel;
        }

        public void setSizeLabel(String sizeLabel) {
            this.sizeLabel = sizeLabel;
        }

        public Integer getSortOrder() {
            return sortOrder;
        }

        public void setSortOrder(Integer sortOrder) {
            this.sortOrder = sortOrder;
        }
    }

    public static class VariantUpdateRequest {
        private String sku;
        @NotBlank
        private String name;
        @NotNull
        private Long amount;
        @NotBlank
        private String currency;
        @NotNull
        private Integer stock;
        private Integer weightGrossG;
        private Integer lengthMm;
        private Integer widthMm;
        private Integer heightMm;
        private String colorCode;
        private String colorLabel;
        private String colorHex;
        private String sizeCode;
        private String sizeLabel;
        private Integer sortOrder;

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

        public Integer getWeightGrossG() {
            return weightGrossG;
        }

        public void setWeightGrossG(Integer weightGrossG) {
            this.weightGrossG = weightGrossG;
        }

        public Integer getLengthMm() {
            return lengthMm;
        }

        public void setLengthMm(Integer lengthMm) {
            this.lengthMm = lengthMm;
        }

        public Integer getWidthMm() {
            return widthMm;
        }

        public void setWidthMm(Integer widthMm) {
            this.widthMm = widthMm;
        }

        public Integer getHeightMm() {
            return heightMm;
        }

        public void setHeightMm(Integer heightMm) {
            this.heightMm = heightMm;
        }

        public String getColorCode() {
            return colorCode;
        }

        public void setColorCode(String colorCode) {
            this.colorCode = colorCode;
        }

        public String getColorLabel() {
            return colorLabel;
        }

        public void setColorLabel(String colorLabel) {
            this.colorLabel = colorLabel;
        }

        public String getColorHex() {
            return colorHex;
        }

        public void setColorHex(String colorHex) {
            this.colorHex = colorHex;
        }

        public String getSizeCode() {
            return sizeCode;
        }

        public void setSizeCode(String sizeCode) {
            this.sizeCode = sizeCode;
        }

        public String getSizeLabel() {
            return sizeLabel;
        }

        public void setSizeLabel(String sizeLabel) {
            this.sizeLabel = sizeLabel;
        }

        public Integer getSortOrder() {
            return sortOrder;
        }

        public void setSortOrder(Integer sortOrder) {
            this.sortOrder = sortOrder;
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
        private boolean isActive;
        private com.example.common.domain.Money price;
        private com.example.common.domain.Money oldPrice;
        private boolean onSale;
        private Integer discountPercent;
        private List<ImageResponse> images;
        private OffsetDateTime createdAt;
        private OffsetDateTime updatedAt;
        private List<VariantResponse> variants;
        private List<SpecificationSection> specifications;
        private CatalogueContentModels.CataloguePresentation presentation;

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

        public boolean getIsActive() {
            return isActive;
        }

        public void setIsActive(boolean isActive) {
            this.isActive = isActive;
        }

        public com.example.common.domain.Money getPrice() {
            return price;
        }

        public void setPrice(com.example.common.domain.Money price) {
            this.price = price;
        }

        public com.example.common.domain.Money getOldPrice() {
            return oldPrice;
        }

        public void setOldPrice(com.example.common.domain.Money oldPrice) {
            this.oldPrice = oldPrice;
        }

        public boolean isOnSale() {
            return onSale;
        }

        public void setOnSale(boolean onSale) {
            this.onSale = onSale;
        }

        public Integer getDiscountPercent() {
            return discountPercent;
        }

        public void setDiscountPercent(Integer discountPercent) {
            this.discountPercent = discountPercent;
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

        public CatalogueContentModels.CataloguePresentation getPresentation() {
            return presentation;
        }

        public void setPresentation(CatalogueContentModels.CataloguePresentation presentation) {
            this.presentation = presentation;
        }
    }

    public static class VariantResponse {
        private UUID id;
        private String sku;
        private String name;
        private com.example.common.domain.Money price;
        private com.example.common.domain.Money oldPrice;
        private boolean onSale;
        private Integer discountPercent;
        private String salePromotionName;
        private int stock;
        private Integer weightGrossG;
        private Integer lengthMm;
        private Integer widthMm;
        private Integer heightMm;
        private String colorCode;
        private String colorLabel;
        private String colorHex;
        private String sizeCode;
        private String sizeLabel;
        private Integer sortOrder;

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

        public com.example.common.domain.Money getOldPrice() {
            return oldPrice;
        }

        public void setOldPrice(com.example.common.domain.Money oldPrice) {
            this.oldPrice = oldPrice;
        }

        public boolean isOnSale() {
            return onSale;
        }

        public void setOnSale(boolean onSale) {
            this.onSale = onSale;
        }

        public Integer getDiscountPercent() {
            return discountPercent;
        }

        public void setDiscountPercent(Integer discountPercent) {
            this.discountPercent = discountPercent;
        }

        public String getSalePromotionName() {
            return salePromotionName;
        }

        public void setSalePromotionName(String salePromotionName) {
            this.salePromotionName = salePromotionName;
        }

        public int getStock() {
            return stock;
        }

        public void setStock(int stock) {
            this.stock = stock;
        }

        public Integer getWeightGrossG() {
            return weightGrossG;
        }

        public void setWeightGrossG(Integer weightGrossG) {
            this.weightGrossG = weightGrossG;
        }

        public Integer getLengthMm() {
            return lengthMm;
        }

        public void setLengthMm(Integer lengthMm) {
            this.lengthMm = lengthMm;
        }

        public Integer getWidthMm() {
            return widthMm;
        }

        public void setWidthMm(Integer widthMm) {
            this.widthMm = widthMm;
        }

        public Integer getHeightMm() {
            return heightMm;
        }

        public void setHeightMm(Integer heightMm) {
            this.heightMm = heightMm;
        }

        public String getColorCode() {
            return colorCode;
        }

        public void setColorCode(String colorCode) {
            this.colorCode = colorCode;
        }

        public String getColorLabel() {
            return colorLabel;
        }

        public void setColorLabel(String colorLabel) {
            this.colorLabel = colorLabel;
        }

        public String getColorHex() {
            return colorHex;
        }

        public void setColorHex(String colorHex) {
            this.colorHex = colorHex;
        }

        public String getSizeCode() {
            return sizeCode;
        }

        public void setSizeCode(String sizeCode) {
            this.sizeCode = sizeCode;
        }

        public String getSizeLabel() {
            return sizeLabel;
        }

        public void setSizeLabel(String sizeLabel) {
            this.sizeLabel = sizeLabel;
        }

        public Integer getSortOrder() {
            return sortOrder;
        }

        public void setSortOrder(Integer sortOrder) {
            this.sortOrder = sortOrder;
        }
    }

    public static class ImageResponse {
        private UUID id;
        private String url;
        private int position;
        private UUID variantId;
        private MediaModels.MediaManifest media;

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

        public MediaModels.MediaManifest getMedia() {
            return media;
        }

        public void setMedia(MediaModels.MediaManifest media) {
            this.media = media;
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

}
