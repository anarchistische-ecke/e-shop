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
            AnnouncementBanner announcementBanner,
            OffsetDateTime publishedAt
    ) {
    }

    public record AnnouncementBanner(
            String id,
            String shortText,
            String styleVariant,
            String primaryCtaLabel,
            String primaryCtaUrl,
            String secondaryCtaLabel,
            String secondaryCtaUrl,
            OffsetDateTime activeFrom,
            OffsetDateTime activeTo,
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
            Integer sort,
            String pageSlug,
            String pagePath
    ) {
        public NavigationItem(
                String label,
                String url,
                String itemType,
                Boolean openInNewTab,
                String visibility,
                Integer sort
        ) {
            this(label, url, itemType, openInNewTab, visibility, sort, null, null);
        }
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
            List<PageSection> sections,
            String robots
    ) {
        public Page(
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
            this(
                    slug, path, title, template, navLabel, summary, seoTitle, seoDescription,
                    seoImage, publishedAt, sections, "index,follow"
            );
        }
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
            List<PageSectionItem> items,
            String campaignPlacement,
            Integer itemLimit,
            String storefrontCollectionKey,
            List<MarketingContentModels.Creative> banners,
            List<FaqItem> faqs,
            List<LegalDocumentSummary> legalDocuments,
            List<MarketingContentModels.Campaign> campaigns
    ) {
        public PageSection(
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
            this(
                    internalName, sectionType, sort, anchorId, eyebrow, title, accent, body,
                    image, mobileImage, primaryCtaLabel, primaryCtaUrl, secondaryCtaLabel,
                    secondaryCtaUrl, styleVariant, layoutVariant, publishedAt, items,
                    null, null, null, List.of(), List.of(), List.of(), List.of()
            );
        }
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
            OffsetDateTime publishedAt,
            String productKey,
            String categoryKey,
            String brandKey,
            String storefrontCollectionKey
    ) {
        public PageSectionItem(
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
            this(
                    title, description, label, url, image, referenceKind, referenceKey,
                    sort, publishedAt, null, null, null, null
            );
        }
    }

    public record FaqItem(
            String id,
            String question,
            String answer,
            String category,
            Integer sort
    ) {
    }

    public record LegalDocumentSummary(
            String key,
            String slug,
            String path,
            String title,
            String summary,
            String versionLabel,
            java.time.LocalDate effectiveFrom,
            Integer sort
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
