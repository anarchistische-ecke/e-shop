package com.example.api.content;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.util.StringUtils;

import java.util.Locale;

@RestController
@RequestMapping("/admin/content/cache")
public class ContentCacheAdminController {

    private final DirectusContentCacheService contentCacheService;

    public ContentCacheAdminController(DirectusContentCacheService contentCacheService) {
        this.contentCacheService = contentCacheService;
    }

    @PostMapping("/invalidate")
    public DirectusContentCacheService.CacheInvalidationResult invalidate(
            @RequestBody(required = false) CacheInvalidationRequest request
    ) {
        String scope = request != null && StringUtils.hasText(request.scope())
                ? request.scope().trim().toLowerCase(Locale.ROOT)
                : "all";

        return switch (scope) {
            case "all" -> contentCacheService.invalidateAll();
            case "site_settings", "site-settings" -> contentCacheService.invalidateSiteSettings();
            case "navigation" -> contentCacheService.invalidateNavigation(request != null ? request.placement() : null);
            case "page" -> contentCacheService.invalidatePage(requireSlug(request));
            default -> throw new IllegalArgumentException("Unsupported content cache scope: " + scope);
        };
    }

    private String requireSlug(CacheInvalidationRequest request) {
        if (request == null || !StringUtils.hasText(request.slug())) {
            throw new IllegalArgumentException("slug is required when invalidating page cache");
        }

        return request.slug();
    }

    public record CacheInvalidationRequest(String scope, String placement, String slug) {
    }
}
