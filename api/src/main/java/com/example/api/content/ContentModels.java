package com.example.api.content;

import java.time.OffsetDateTime;
import java.util.List;

public final class ContentModels {

    private ContentModels() {
    }

    public record SiteSettings(
            String siteName,
            String brandDescription,
            String supportPhone,
            String supportEmail,
            String legalEntityShort,
            String legalEntityFull,
            String legalInn,
            String legalOgrnip,
            String legalAddress,
            Integer copyrightStartYear,
            String defaultSeoTitleSuffix,
            String defaultSeoDescription,
            MediaAsset defaultOgImage,
            OffsetDateTime publishedAt
    ) {
    }

    public record NavigationGroup(
            String key,
            String title,
            String placement,
            String description,
            Integer sort,
            List<NavigationItem> items
    ) {
    }

    public record NavigationItem(
            String label,
            String url,
            String itemType,
            Boolean openInNewTab,
            String visibility,
            Integer sort
    ) {
    }

    public record Page(
            String slug,
            String path,
            String title,
            String template,
            String navLabel,
            String summary,
            String seoTitle,
            String seoDescription,
            MediaAsset seoImage,
            OffsetDateTime publishedAt,
            List<PageSection> sections
    ) {
    }

    public record PageSection(
            String internalName,
            String sectionType,
            Integer sort,
            String anchorId,
            String eyebrow,
            String title,
            String accent,
            String body,
            MediaAsset image,
            MediaAsset mobileImage,
            String primaryCtaLabel,
            String primaryCtaUrl,
            String secondaryCtaLabel,
            String secondaryCtaUrl,
            String styleVariant,
            String layoutVariant,
            OffsetDateTime publishedAt,
            List<PageSectionItem> items
    ) {
    }

    public record PageSectionItem(
            String title,
            String description,
            String label,
            String url,
            MediaAsset image,
            String referenceKind,
            String referenceKey,
            Integer sort,
            OffsetDateTime publishedAt
    ) {
    }

    public record MediaAsset(
            String id,
            String url,
            Integer width,
            Integer height,
            String alt,
            String type
    ) {
    }
}
