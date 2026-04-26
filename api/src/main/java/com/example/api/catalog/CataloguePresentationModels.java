package com.example.api.catalog;

import com.example.api.content.CatalogueContentModels;

public final class CataloguePresentationModels {

    private CataloguePresentationModels() {
    }

    public record OverlayMergeResult(
            CatalogueContentModels.CataloguePresentation presentation,
            boolean overlayReadFailed
    ) {
    }
}
