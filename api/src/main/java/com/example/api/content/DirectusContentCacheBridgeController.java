package com.example.api.content;

import com.example.api.catalog.DirectusBridgeSecurity;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Locale;

@RestController
@RequestMapping("/internal/directus/content/cache")
public class DirectusContentCacheBridgeController {

    private final DirectusContentCacheService cacheService;
    private final DirectusBridgeSecurity bridgeSecurity;

    public DirectusContentCacheBridgeController(
            DirectusContentCacheService cacheService,
            DirectusBridgeSecurity bridgeSecurity
    ) {
        this.cacheService = cacheService;
        this.bridgeSecurity = bridgeSecurity;
    }

    @PostMapping("/invalidate")
    public DirectusContentCacheService.CacheInvalidationResult invalidate(
            @RequestBody(required = false) InvalidationRequest body,
            HttpServletRequest request
    ) {
        bridgeSecurity.authorize(request);
        String scope = body != null && StringUtils.hasText(body.scope())
                ? body.scope().trim().toLowerCase(Locale.ROOT)
                : "all";
        return switch (scope) {
            case "all", "campaign", "campaigns", "banner", "banners" -> cacheService.invalidateAll();
            case "site_settings", "site-settings" -> cacheService.invalidateSiteSettings();
            case "navigation" -> cacheService.invalidateNavigation(body != null ? body.placement() : null);
            case "page" -> cacheService.invalidatePage(require(body != null ? body.slug() : null, "slug"));
            case "collection", "storefront_collection" ->
                    cacheService.invalidateCollection(require(body != null ? body.key() : null, "key"));
            default -> throw new IllegalArgumentException("Unsupported content cache scope: " + scope);
        };
    }

    private String require(String value, String field) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(field + " is required for this invalidation scope");
        }
        return value.trim();
    }

    public record InvalidationRequest(String scope, String placement, String slug, String key) {
    }
}
