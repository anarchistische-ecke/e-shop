package com.example.api.content;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=0, must-revalidate")
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
        ContentModels.Page page = contentService.getPageBySlug(slug);
        return ResponseEntity.ok()
                .header(
                        HttpHeaders.CACHE_CONTROL,
                        hasCampaignSlot(page)
                                ? "private, no-store, max-age=0"
                                : buildPublicCacheControl()
                )
                .body(page);
    }

    @GetMapping("/preview/pages/{slug}")
    public ResponseEntity<ContentModels.Page> getPreviewPageBySlug(@PathVariable String slug) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store, max-age=0")
                .body(contentService.getPreviewPageBySlug(slug));
    }

    private String buildPublicCacheControl() {
        return "public, max-age=" + seconds(properties.getResponseCacheMaxAge())
                + ", stale-while-revalidate=" + seconds(properties.getResponseCacheStaleWhileRevalidate())
                + ", stale-if-error=" + seconds(properties.getResponseCacheStaleIfError());
    }

    private long seconds(java.time.Duration value) {
        return value == null ? 0 : Math.max(0, value.toSeconds());
    }

    private boolean hasCampaignSlot(ContentModels.Page page) {
        return page != null
                && page.sections() != null
                && page.sections().stream()
                .anyMatch(section -> "campaign_slot".equalsIgnoreCase(section.sectionType()));
    }
}
