package com.example.api.content;

import com.example.common.domain.Money;

import java.time.OffsetDateTime;
import java.util.List;

public final class CatalogueContentModels {

    private CatalogueContentModels() {
    }

    public record PresentationSource(
            String mode,
            String overlayKey,
            String overlayStatus,
            OffsetDateTime overlayPublishedAt,
            boolean overlayApplied,
            boolean overlayAvailable
    ) {
    }

    public record CataloguePresentation(
            PresentationSource source,
            String seoTitle,
            String seoDescription,
            ContentModels.MediaAsset seoImage,
            String badgeText,
            String ribbonText,
            String marketingTitle,
            String marketingSubtitle,
            String introBody,
            ContentModels.PageSection hero,
            List<ContentModels.PageSection> blocks,
            List<String> linkedCollectionKeys
    ) {
    }

    public record CatalogueOverlay(
            Integer itemId,
            String key,
            String entityKind,
            String status,
            OffsetDateTime publishedAt,
            String seoTitle,
            String seoDescription,
            ContentModels.MediaAsset seoImage,
            String badgeText,
            String ribbonText,
            String marketingTitle,
            String marketingSubtitle,
            String introBody,
            ContentModels.PageSection hero,
            List<ContentModels.PageSection> blocks,
            List<String> linkedCollectionKeys
    ) {
    }

    public record StorefrontCollection(
            String key,
            String title,
            String description,
            String mode,
            String ruleType,
            String categoryKey,
            String brandKey,
            Integer limit,
            String sortMode,
            String seoTitle,
            String seoDescription,
            ContentModels.MediaAsset seoImage,
            ContentModels.PageSection hero,
            String primaryCtaLabel,
            String primaryCtaUrl,
            OffsetDateTime publishedAt,
            List<StorefrontCollectionEntry> items
    ) {
    }

    public record StorefrontCollectionDefinition(
            String key,
            String title,
            String description,
            String mode,
            String ruleType,
            String categoryKey,
            String brandKey,
            Integer limit,
            String sortMode,
            String seoTitle,
            String seoDescription,
            ContentModels.MediaAsset seoImage,
            ContentModels.PageSection hero,
            String primaryCtaLabel,
            String primaryCtaUrl,
            String status,
            OffsetDateTime publishedAt,
            List<StorefrontCollectionRuleItem> rules
    ) {
    }

    public record StorefrontCollectionRuleItem(
            String entityKind,
            String entityKey,
            String behavior,
            Integer sort
    ) {
    }

    public record StorefrontCollectionEntry(
            String entityKind,
            String entityKey,
            String entityId,
            String source,
            String title,
            String summary,
            String slug,
            String href,
            ContentModels.MediaAsset image,
            Money price,
            String brandKey,
            List<String> categoryKeys,
            CataloguePresentation presentation
    ) {
    }
}
