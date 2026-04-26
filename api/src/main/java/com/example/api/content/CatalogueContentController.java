package com.example.api.content;

import com.example.api.catalog.CataloguePresentationService;
import com.example.api.catalog.CatalogueResponseFactory;
import com.example.catalog.service.CatalogService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

@RestController
@RequestMapping("/content")
public class CatalogueContentController {

    private final CataloguePresentationService presentationService;
    private final CatalogueResponseFactory responseFactory;
    private final CatalogService catalogService;
    private final DirectusContentProperties properties;

    public CatalogueContentController(
            CataloguePresentationService presentationService,
            CatalogueResponseFactory responseFactory,
            CatalogService catalogService,
            DirectusContentProperties properties
    ) {
        this.presentationService = presentationService;
        this.responseFactory = responseFactory;
        this.catalogService = catalogService;
        this.properties = properties;
    }

    @GetMapping("/collections/{key}")
    public ResponseEntity<CatalogueContentModels.StorefrontCollection> getCollection(@PathVariable String key) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, buildPublicCacheControl())
                .body(presentationService.getPublishedStorefrontCollection(key));
    }

    @GetMapping("/preview/collections/{key}")
    public ResponseEntity<CatalogueContentModels.StorefrontCollection> getPreviewCollection(@PathVariable String key) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store, max-age=0")
                .body(presentationService.getPreviewStorefrontCollection(key));
    }

    @GetMapping("/preview/catalogue/products/{productKey}")
    public ResponseEntity<com.example.api.catalog.CatalogController.ProductResponse> getPreviewProduct(@PathVariable String productKey) {
        var product = catalogService.getProductBySlug(productKey)
                .orElseThrow(() -> new ContentNotFoundException("Preview product not found in backend for key: " + productKey));

        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store, max-age=0")
                .body(responseFactory.toProductResponse(product, presentationService.buildPreviewProductPresentation(product).presentation()));
    }

    @GetMapping("/preview/catalogue/categories/{categoryKey}")
    public ResponseEntity<com.example.api.catalog.CategoryController.CategoryResponse> getPreviewCategory(@PathVariable String categoryKey) {
        var category = catalogService.getBySlug(categoryKey)
                .orElseThrow(() -> new ContentNotFoundException("Preview category not found in backend for key: " + categoryKey));

        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store, max-age=0")
                .body(responseFactory.toCategoryResponse(category, presentationService.buildPreviewCategoryPresentation(category).presentation()));
    }

    private String buildPublicCacheControl() {
        long maxAgeSeconds = toCacheSeconds(properties.getResponseCacheMaxAge());
        StringBuilder value = new StringBuilder("public, max-age=").append(maxAgeSeconds);

        appendDirective(value, "stale-while-revalidate", properties.getResponseCacheStaleWhileRevalidate());
        appendDirective(value, "stale-if-error", properties.getResponseCacheStaleIfError());
        return value.toString();
    }

    private void appendDirective(StringBuilder value, String directive, Duration duration) {
        long seconds = toCacheSeconds(duration);
        if (seconds <= 0) {
            return;
        }

        value.append(", ").append(directive).append("=").append(seconds);
    }

    private long toCacheSeconds(Duration duration) {
        if (duration == null || duration.isNegative()) {
            return 0;
        }

        return duration.getSeconds();
    }
}
