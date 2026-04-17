package com.example.api.content;

import java.util.Collection;
import java.util.Map;

public interface CatalogueContentService {

    Map<String, CatalogueContentModels.CatalogueOverlay> getPublishedProductOverlays(Collection<String> productKeys);

    Map<String, CatalogueContentModels.CatalogueOverlay> getPreviewProductOverlays(Collection<String> productKeys);

    Map<String, CatalogueContentModels.CatalogueOverlay> getPublishedCategoryOverlays(Collection<String> categoryKeys);

    Map<String, CatalogueContentModels.CatalogueOverlay> getPreviewCategoryOverlays(Collection<String> categoryKeys);

    CatalogueContentModels.StorefrontCollectionDefinition getStorefrontCollection(String key);

    CatalogueContentModels.StorefrontCollectionDefinition getPreviewStorefrontCollection(String key);
}
