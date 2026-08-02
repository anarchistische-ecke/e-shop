package com.example.api.content;

import org.springframework.http.CacheControl;
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
public class MarketingContentController {

    private final MarketingContentService marketingContentService;
    private final DirectusContentProperties properties;

    public MarketingContentController(
            MarketingContentService marketingContentService,
            DirectusContentProperties properties
    ) {
        this.marketingContentService = marketingContentService;
        this.properties = properties;
    }

    @GetMapping("/campaigns/active")
    public ResponseEntity<List<MarketingContentModels.Campaign>> activeCampaigns(
            @RequestParam(value = "placement", required = false) String placement,
            @RequestParam(value = "limit", required = false) Integer limit
    ) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(marketingContentService.getActiveCampaigns(placement, limit));
    }

    @GetMapping("/campaigns/{slug}")
    public ResponseEntity<MarketingContentModels.Campaign> activeCampaign(@PathVariable String slug) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(marketingContentService.getActiveCampaignBySlug(slug));
    }

    @GetMapping("/legal-documents/{keyOrSlug}")
    public ResponseEntity<MarketingContentModels.LegalDocument> legalDocument(
            @PathVariable String keyOrSlug
    ) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, ordinaryCacheControl())
                .body(marketingContentService.getLegalDocument(keyOrSlug));
    }

    private String ordinaryCacheControl() {
        return "public, max-age=" + seconds(properties.getResponseCacheMaxAge())
                + ", stale-while-revalidate=" + seconds(properties.getResponseCacheStaleWhileRevalidate())
                + ", stale-if-error=" + seconds(properties.getResponseCacheStaleIfError());
    }

    private long seconds(java.time.Duration value) {
        return value == null ? 0 : Math.max(0, value.toSeconds());
    }
}
