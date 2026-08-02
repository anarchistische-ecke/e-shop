package com.example.api.content;

import java.time.OffsetDateTime;
import java.util.List;

public final class MarketingContentModels {

    private MarketingContentModels() {
    }

    public record Campaign(
            String id,
            String slug,
            String internalName,
            Integer priority,
            Integer sort,
            OffsetDateTime activeFrom,
            OffsetDateTime activeTo,
            PageLink landingPage,
            StorefrontCollectionLink storefrontCollection,
            OperationalPromotionFacts promotion,
            List<Creative> creatives
    ) {
    }

    public record Creative(
            String id,
            String placement,
            String bannerType,
            Integer priority,
            Integer sort,
            String shortText,
            String eyebrow,
            String title,
            String description,
            ContentModels.MediaAsset image,
            ContentModels.MediaAsset mobileImage,
            String primaryCtaLabel,
            String primaryCtaUrl,
            String secondaryCtaLabel,
            String secondaryCtaUrl,
            String styleVariant,
            String layoutVariant,
            OffsetDateTime activeFrom,
            OffsetDateTime activeTo
    ) {
    }

    public record OperationalPromotionFacts(
            String kind,
            String id,
            String name,
            String code,
            String type,
            Integer discountPercent,
            Long discountAmount,
            Long salePriceAmount,
            Long thresholdAmount,
            String currency,
            OffsetDateTime startsAt,
            OffsetDateTime endsAt,
            String description
    ) {
    }

    public record PageLink(String slug, String path, String title) {
    }

    public record StorefrontCollectionLink(String key, String title) {
    }

    public record LegalDocument(
            String key,
            String slug,
            String path,
            String title,
            String summary,
            String bodyHtml,
            String versionLabel,
            java.time.LocalDate effectiveFrom,
            OffsetDateTime publishedAt
    ) {
    }
}
