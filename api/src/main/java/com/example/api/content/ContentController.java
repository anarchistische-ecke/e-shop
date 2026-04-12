package com.example.api.content;

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

    public ContentController(ContentService contentService) {
        this.contentService = contentService;
    }

    @GetMapping("/site-settings")
    public ContentModels.SiteSettings getSiteSettings() {
        return contentService.getSiteSettings();
    }

    @GetMapping("/preview/site-settings")
    public ContentModels.SiteSettings getPreviewSiteSettings() {
        return contentService.getPreviewSiteSettings();
    }

    @GetMapping("/navigation")
    public List<ContentModels.NavigationGroup> getNavigation(
            @RequestParam(value = "placement", required = false) String placement
    ) {
        return contentService.getNavigation(placement);
    }

    @GetMapping("/preview/navigation")
    public List<ContentModels.NavigationGroup> getPreviewNavigation(
            @RequestParam(value = "placement", required = false) String placement
    ) {
        return contentService.getPreviewNavigation(placement);
    }

    @GetMapping("/pages/{slug}")
    public ContentModels.Page getPageBySlug(@PathVariable String slug) {
        return contentService.getPageBySlug(slug);
    }

    @GetMapping("/preview/pages/{slug}")
    public ContentModels.Page getPreviewPageBySlug(@PathVariable String slug) {
        return contentService.getPreviewPageBySlug(slug);
    }
}
