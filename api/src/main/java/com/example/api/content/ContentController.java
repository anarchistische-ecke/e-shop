package com.example.api.content;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.List;

@RestController
@RequestMapping("/content")
public class ContentController {

    private final ContentService contentService;
    private final DirectusContentProperties properties;

    public ContentController(ContentService contentService, DirectusContentProperties properties) {
        this.contentService = contentService;
        this.properties = properties;
    }

    @GetMapping("/site-settings")
    public ResponseEntity<ContentModels.SiteSettings> getSiteSettings() {
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, buildPublicCacheControl())
                .body(contentService.getSiteSettings());
    }

    @GetMapping("/preview/site-settings")
    public ResponseEntity<ContentModels.SiteSettings> getPreviewSiteSettings() {
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store, max-age=0")
                .body(contentService.getPreviewSiteSettings());
    }

    @GetMapping("/navigation")
    public ResponseEntity<List<ContentModels.NavigationGroup>> getNavigation(
            @RequestParam(value = "placement", required = false) String placement
    ) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, buildPublicCacheControl())
                .body(contentService.getNavigation(placement));
    }

    @GetMapping("/preview/navigation")
    public ResponseEntity<List<ContentModels.NavigationGroup>> getPreviewNavigation(
            @RequestParam(value = "placement", required = false) String placement
    ) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store, max-age=0")
                .body(contentService.getPreviewNavigation(placement));
    }

    @GetMapping("/pages/{slug}")
    public ResponseEntity<ContentModels.Page> getPageBySlug(@PathVariable String slug) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, buildPublicCacheControl())
                .body(contentService.getPageBySlug(slug));
    }

    @GetMapping("/preview/pages/{slug}")
    public ResponseEntity<ContentModels.Page> getPreviewPageBySlug(@PathVariable String slug) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store, max-age=0")
                .body(contentService.getPreviewPageBySlug(slug));
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
