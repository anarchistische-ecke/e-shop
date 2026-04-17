package com.example.api.content;

import java.util.List;

public interface ContentService {

    ContentModels.SiteSettings getSiteSettings();

    ContentModels.SiteSettings getPreviewSiteSettings();

    List<ContentModels.NavigationGroup> getNavigation(String placement);

    List<ContentModels.NavigationGroup> getPreviewNavigation(String placement);

    ContentModels.Page getPageBySlug(String slug);

    ContentModels.Page getPreviewPageBySlug(String slug);
}
