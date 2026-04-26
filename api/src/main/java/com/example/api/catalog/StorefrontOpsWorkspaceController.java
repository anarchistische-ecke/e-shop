package com.example.api.catalog;

import com.example.admin.service.AdminActivityService;
import com.example.api.content.CatalogueContentModels;
import com.example.api.content.CatalogueContentService;
import com.example.catalog.domain.Brand;
import com.example.catalog.domain.Category;
import com.example.catalog.domain.Product;
import com.example.catalog.domain.ProductVariant;
import com.example.catalog.service.CatalogService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/internal/directus/catalogue/workspace")
public class StorefrontOpsWorkspaceController {

    private final CatalogService catalogService;
    private final CatalogueContentService catalogueContentService;
    private final StorefrontOpsWorkspaceFactory workspaceFactory;
    private final DirectusBridgeSecurity bridgeSecurity;
    private final AdminActivityService adminActivityService;
    private final ObjectMapper objectMapper;

    public StorefrontOpsWorkspaceController(
            CatalogService catalogService,
            CatalogueContentService catalogueContentService,
            StorefrontOpsWorkspaceFactory workspaceFactory,
            DirectusBridgeSecurity bridgeSecurity,
            AdminActivityService adminActivityService,
            ObjectMapper objectMapper
    ) {
        this.catalogService = catalogService;
        this.catalogueContentService = catalogueContentService;
        this.workspaceFactory = workspaceFactory;
        this.bridgeSecurity = bridgeSecurity;
        this.adminActivityService = adminActivityService;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/products")
    public ResponseEntity<StorefrontOpsWorkspaceModels.ProductWorkspaceList> listProducts(
            @RequestParam(name = "q", required = false) String query,
            @RequestParam(name = "includeInactive", defaultValue = "true") boolean includeInactive,
            HttpServletRequest request
    ) {
        DirectusBridgeSecurity.DirectusBridgePrincipal principal = authorize(request);
        List<Product> products = catalogService.getAllProducts().stream()
                .filter(product -> includeInactive || product.isIsActive())
                .filter(product -> matchesQuery(query,
                        product.getName(),
                        product.getSlug(),
                        product.getDescription(),
                        product.getBrand() != null ? product.getBrand().getName() : null,
                        product.getBrand() != null ? product.getBrand().getSlug() : null,
                        joinCategoryValues(product.getCategories())
                ))
                .sorted((left, right) -> compareNullableStrings(left.getName(), right.getName()))
                .toList();
        OverlayLookupResult productOverlays = loadPreviewProductOverlays(products);
        audit(principal, "catalogue.workspace.products.list", detailMap(
                "query", query,
                "includeInactive", includeInactive,
                "count", products.size()
        ));
        return ResponseEntity.ok(new StorefrontOpsWorkspaceModels.ProductWorkspaceList(
                products.stream()
                        .map(product -> workspaceFactory.toProductSummary(product, productOverlays.overlays().get(normalize(product.getSlug()))))
                        .toList(),
                workspaceFactory.toBrandOptions(catalogService.listAllInBrand()),
                workspaceFactory.toCategoryOptions(catalogService.listAllInCategory()),
                productOverlays.readFailed()
        ));
    }

    @GetMapping("/products/{idOrSlug}")
    public ResponseEntity<StorefrontOpsWorkspaceModels.ProductWorkspaceDetail> getProduct(
            @PathVariable String idOrSlug,
            HttpServletRequest request
    ) {
        DirectusBridgeSecurity.DirectusBridgePrincipal principal = authorize(request);
        Product product = resolveProduct(idOrSlug);
        OverlayLookupResult productOverlays = loadPreviewProductOverlays(List.of(product));
        audit(principal, "catalogue.workspace.product.get", Map.of("product", product.getSlug()));
        return ResponseEntity.ok(new StorefrontOpsWorkspaceModels.ProductWorkspaceDetail(
                workspaceFactory.toProductDetail(product, productOverlays.overlays().get(normalize(product.getSlug()))),
                workspaceFactory.toBrandOptions(catalogService.listAllInBrand()),
                workspaceFactory.toCategoryOptions(catalogService.listAllInCategory()),
                productOverlays.readFailed()
        ));
    }

    @GetMapping("/categories")
    public ResponseEntity<StorefrontOpsWorkspaceModels.CategoryWorkspaceList> listCategories(
            @RequestParam(name = "q", required = false) String query,
            @RequestParam(name = "includeInactive", defaultValue = "true") boolean includeInactive,
            HttpServletRequest request
    ) {
        DirectusBridgeSecurity.DirectusBridgePrincipal principal = authorize(request);
        List<Category> categories = catalogService.listAllInCategory().stream()
                .filter(category -> includeInactive || category.isIsActive())
                .filter(category -> matchesQuery(query, category.getName(), category.getSlug(), category.getFullPath(), category.getDescription()))
                .sorted((left, right) -> compareNullableStrings(
                        left.getFullPath() != null ? left.getFullPath() : left.getSlug(),
                        right.getFullPath() != null ? right.getFullPath() : right.getSlug()
                ))
                .toList();
        OverlayLookupResult categoryOverlays = loadPreviewCategoryOverlays(categories);
        audit(principal, "catalogue.workspace.categories.list", detailMap(
                "query", query,
                "includeInactive", includeInactive,
                "count", categories.size()
        ));
        return ResponseEntity.ok(new StorefrontOpsWorkspaceModels.CategoryWorkspaceList(
                categories.stream()
                        .map(category -> workspaceFactory.toCategorySummary(category, categoryOverlays.overlays().get(normalize(category.getSlug()))))
                        .toList(),
                workspaceFactory.toCategoryOptions(catalogService.listAllInCategory()),
                categoryOverlays.readFailed()
        ));
    }

    @GetMapping("/categories/{idOrSlug}")
    public ResponseEntity<StorefrontOpsWorkspaceModels.CategoryWorkspaceDetail> getCategory(
            @PathVariable String idOrSlug,
            HttpServletRequest request
    ) {
        DirectusBridgeSecurity.DirectusBridgePrincipal principal = authorize(request);
        Category category = resolveCategory(idOrSlug);
        OverlayLookupResult categoryOverlays = loadPreviewCategoryOverlays(List.of(category));
        audit(principal, "catalogue.workspace.category.get", Map.of("category", category.getSlug()));
        return ResponseEntity.ok(new StorefrontOpsWorkspaceModels.CategoryWorkspaceDetail(
                workspaceFactory.toCategoryDetail(category, categoryOverlays.overlays().get(normalize(category.getSlug()))),
                workspaceFactory.toCategoryOptions(catalogService.listAllInCategory()),
                categoryOverlays.readFailed()
        ));
    }

    @GetMapping("/brands")
    public ResponseEntity<StorefrontOpsWorkspaceModels.BrandWorkspaceList> listBrands(
            @RequestParam(name = "q", required = false) String query,
            HttpServletRequest request
    ) {
        DirectusBridgeSecurity.DirectusBridgePrincipal principal = authorize(request);
        List<Product> products = catalogService.getAllProducts();
        Map<UUID, Long> productCountsByBrandId = products.stream()
                .map(Product::getBrand)
                .filter(Objects::nonNull)
                .collect(Collectors.groupingBy(Brand::getId, Collectors.counting()));
        List<Brand> brands = catalogService.listAllInBrand().stream()
                .filter(brand -> matchesQuery(query, brand.getName(), brand.getSlug(), brand.getDescription()))
                .sorted((left, right) -> compareNullableStrings(left.getName(), right.getName()))
                .toList();
        audit(principal, "catalogue.workspace.brands.list", detailMap("query", query, "count", brands.size()));
        return ResponseEntity.ok(new StorefrontOpsWorkspaceModels.BrandWorkspaceList(
                brands.stream()
                        .map(brand -> workspaceFactory.toBrandSummary(brand, productCountsByBrandId.getOrDefault(brand.getId(), 0L)))
                        .toList()
        ));
    }

    @GetMapping("/brands/{idOrSlug}")
    public ResponseEntity<StorefrontOpsWorkspaceModels.BrandWorkspaceDetail> getBrand(
            @PathVariable String idOrSlug,
            HttpServletRequest request
    ) {
        DirectusBridgeSecurity.DirectusBridgePrincipal principal = authorize(request);
        Brand brand = resolveBrand(idOrSlug);
        long productCount = catalogService.getAllProducts().stream()
                .filter(product -> product.getBrand() != null && brand.getId().equals(product.getBrand().getId()))
                .count();
        audit(principal, "catalogue.workspace.brand.get", Map.of("brand", brand.getSlug()));
        return ResponseEntity.ok(new StorefrontOpsWorkspaceModels.BrandWorkspaceDetail(
                workspaceFactory.toBrandDetail(brand, productCount)
        ));
    }

    @GetMapping("/inventory")
    public ResponseEntity<StorefrontOpsWorkspaceModels.InventoryWorkspaceList> listInventory(
            @RequestParam(name = "q", required = false) String query,
            @RequestParam(name = "includeInactive", defaultValue = "true") boolean includeInactive,
            HttpServletRequest request
    ) {
        DirectusBridgeSecurity.DirectusBridgePrincipal principal = authorize(request);
        List<StorefrontOpsWorkspaceModels.InventoryRow> items = catalogService.getAllProducts().stream()
                .filter(product -> includeInactive || product.isIsActive())
                .flatMap(product -> product.getVariants().stream().map(variant -> workspaceFactory.toInventoryRow(product, variant)))
                .filter(row -> matchesQuery(query,
                        row.productName(),
                        row.productSlug(),
                        row.variantName(),
                        row.sku(),
                        row.brand() != null ? row.brand().name() : null,
                        row.brand() != null ? row.brand().slug() : null,
                        row.categories().stream().map(StorefrontOpsWorkspaceModels.CategoryOption::fullPath).collect(Collectors.joining(" "))
                ))
                .sorted((left, right) -> compareNullableStrings(left.productName(), right.productName()))
                .toList();
        audit(principal, "catalogue.workspace.inventory.list", detailMap(
                "query", query,
                "includeInactive", includeInactive,
                "count", items.size()
        ));
        return ResponseEntity.ok(new StorefrontOpsWorkspaceModels.InventoryWorkspaceList(items));
    }

    @GetMapping("/summary")
    public ResponseEntity<StorefrontOpsWorkspaceModels.NavigationSummary> summary(HttpServletRequest request) {
        DirectusBridgeSecurity.DirectusBridgePrincipal principal = authorize(request);
        List<Product> products = catalogService.getAllProducts();
        int inventoryCount = products.stream()
                .map(Product::getVariants)
                .filter(Objects::nonNull)
                .mapToInt(variants -> variants.size())
                .sum();
        StorefrontOpsWorkspaceModels.NavigationSummary summary = new StorefrontOpsWorkspaceModels.NavigationSummary(
                products.size(),
                catalogService.listAllInCategory().size(),
                catalogService.listAllInBrand().size(),
                inventoryCount
        );
        audit(principal, "catalogue.workspace.summary", detailMap(
                "products", summary.productCount(),
                "categories", summary.categoryCount(),
                "brands", summary.brandCount(),
                "inventory", summary.inventoryCount()
        ));
        return ResponseEntity.ok(summary);
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

    private Brand resolveBrand(String idOrSlug) {
        try {
            return catalogService.getById(UUID.fromString(idOrSlug))
                    .orElseThrow(() -> new IllegalArgumentException("Brand not found: " + idOrSlug));
        } catch (IllegalArgumentException ignored) {
            return catalogService.getByBrandSlug(idOrSlug)
                    .orElseThrow(() -> new IllegalArgumentException("Brand not found: " + idOrSlug));
        }
    }

    private OverlayLookupResult loadPreviewProductOverlays(Collection<Product> products) {
        List<String> keys = products.stream()
                .map(Product::getSlug)
                .map(this::normalize)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
        if (keys.isEmpty()) {
            return new OverlayLookupResult(Map.of(), false);
        }
        try {
            return new OverlayLookupResult(catalogueContentService.getPreviewProductOverlays(keys), false);
        } catch (Exception error) {
            return new OverlayLookupResult(Map.of(), true);
        }
    }

    private OverlayLookupResult loadPreviewCategoryOverlays(Collection<Category> categories) {
        List<String> keys = categories.stream()
                .map(Category::getSlug)
                .map(this::normalize)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
        if (keys.isEmpty()) {
            return new OverlayLookupResult(Map.of(), false);
        }
        try {
            return new OverlayLookupResult(catalogueContentService.getPreviewCategoryOverlays(keys), false);
        } catch (Exception error) {
            return new OverlayLookupResult(Map.of(), true);
        }
    }

    private boolean matchesQuery(String query, String... values) {
        if (!StringUtils.hasText(query)) {
            return true;
        }
        String normalizedQuery = query.trim().toLowerCase();
        for (String value : values) {
            if (StringUtils.hasText(value) && value.toLowerCase().contains(normalizedQuery)) {
                return true;
            }
        }
        return false;
    }

    private String joinCategoryValues(Collection<Category> categories) {
        if (categories == null || categories.isEmpty()) {
            return "";
        }
        return categories.stream()
                .map(category -> category.getFullPath() != null ? category.getFullPath() : category.getName())
                .filter(StringUtils::hasText)
                .collect(Collectors.joining(" "));
    }

    private int compareNullableStrings(String left, String right) {
        if (left == null && right == null) {
            return 0;
        }
        if (left == null) {
            return 1;
        }
        if (right == null) {
            return -1;
        }
        return String.CASE_INSENSITIVE_ORDER.compare(left, right);
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

    private Map<String, Object> detailMap(Object... entries) {
        Map<String, Object> payload = new LinkedHashMap<>();
        if (entries == null) {
            return payload;
        }
        for (int index = 0; index + 1 < entries.length; index += 2) {
            payload.put(String.valueOf(entries[index]), entries[index + 1]);
        }
        return payload;
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim().toLowerCase() : null;
    }

    private record OverlayLookupResult(
            Map<String, CatalogueContentModels.CatalogueOverlay> overlays,
            boolean readFailed
    ) {
    }
}
