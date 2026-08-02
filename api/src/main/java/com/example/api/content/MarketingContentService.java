package com.example.api.content;

import com.example.api.admincms.DirectusAdminModels.PromoCodeView;
import com.example.api.admincms.DirectusAdminModels.PromotionView;
import com.example.api.admincms.DirectusAdminService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class MarketingContentService {

    private final DirectusMarketingClient client;
    private final DirectusContentClient contentClient;
    private final DirectusAdminService adminService;
    private final CmsHtmlSanitizer htmlSanitizer;
    private final Clock clock;
    private final boolean enabled;

    @Autowired
    public MarketingContentService(
            DirectusMarketingClient client,
            DirectusContentClient contentClient,
            DirectusAdminService adminService,
            CmsHtmlSanitizer htmlSanitizer,
            DirectusContentProperties properties
    ) {
        this(
                client,
                contentClient,
                adminService,
                htmlSanitizer,
                Clock.systemUTC(),
                properties.isMarketingV2Enabled()
        );
    }

    MarketingContentService(
            DirectusMarketingClient client,
            DirectusContentClient contentClient,
            DirectusAdminService adminService,
            CmsHtmlSanitizer htmlSanitizer,
            Clock clock,
            boolean enabled
    ) {
        this.client = client;
        this.contentClient = contentClient;
        this.adminService = adminService;
        this.htmlSanitizer = htmlSanitizer;
        this.clock = clock;
        this.enabled = enabled;
    }

    public List<MarketingContentModels.Campaign> getActiveCampaigns(String placement, Integer requestedLimit) {
        return resolveActiveCampaigns(placement, requestedLimit, 12);
    }

    private List<MarketingContentModels.Campaign> resolveActiveCampaigns(
            String placement,
            Integer requestedLimit,
            int maximumLimit
    ) {
        if (!enabled) return List.of();
        String normalizedPlacement = normalizePlacement(placement);
        int limit = normalizedPlacement.equals("sitewide_announcement")
                ? 1
                : normalizeLimit(requestedLimit, maximumLimit);
        OffsetDateTime now = OffsetDateTime.now(clock);
        List<DirectusMarketingClient.DirectusCampaign> campaigns = client.fetchPublishedCampaigns()
                .stream()
                .filter(campaign -> isEffective(campaign.activeFrom(), campaign.activeTo(), now))
                .toList();
        if (campaigns.isEmpty()) return List.of();

        Map<UUID, PromotionView> promotions = adminService.activePromotions().stream()
                .collect(Collectors.toMap(PromotionView::id, Function.identity(), (left, right) -> left));
        Map<UUID, PromoCodeView> promoCodes = adminService.listPromoCodes().stream()
                .filter(PromoCodeView::activeNow)
                .collect(Collectors.toMap(PromoCodeView::id, Function.identity(), (left, right) -> left));
        List<DirectusMarketingClient.DirectusCreative> creatives = client.fetchPublishedCreatives(
                campaigns.stream().map(DirectusMarketingClient.DirectusCampaign::id).toList(),
                normalizedPlacement
        );
        Set<String> fileIds = new LinkedHashSet<>();
        creatives.forEach(creative -> {
            addFileId(fileIds, creative.image());
            addFileId(fileIds, creative.mobileImage());
        });
        Map<String, DirectusContentClient.DirectusFileAsset> files = contentClient.fetchFiles(fileIds)
                .stream()
                .collect(Collectors.toMap(
                        DirectusContentClient.DirectusFileAsset::id,
                        Function.identity(),
                        (left, right) -> left
                ));
        Map<Integer, List<DirectusMarketingClient.DirectusCreative>> creativesByCampaign = creatives.stream()
                .filter(creative -> isEffective(creative.activeFrom(), creative.activeTo(), now))
                .collect(Collectors.groupingBy(DirectusMarketingClient.DirectusCreative::campaign));

        Comparator<MarketingContentModels.Campaign> order = Comparator
                .comparing(MarketingContentModels.Campaign::priority, Comparator.nullsFirst(Integer::compareTo))
                .reversed()
                .thenComparing(MarketingContentModels.Campaign::sort, Comparator.nullsLast(Integer::compareTo))
                .thenComparing(MarketingContentModels.Campaign::id);

        return campaigns.stream()
                .map(campaign -> toCampaign(
                        campaign,
                        creativesByCampaign.getOrDefault(campaign.id(), List.of()),
                        promotions,
                        promoCodes,
                        files
                ))
                .filter(campaign -> campaign != null && !campaign.creatives().isEmpty())
                .sorted(order)
                .limit(limit)
                .toList();
    }

    public MarketingContentModels.Campaign getActiveCampaignBySlug(String slug) {
        if (!StringUtils.hasText(slug)) throw new IllegalArgumentException("Campaign slug is required");
        String normalizedSlug = slug.trim().toLowerCase(Locale.ROOT);
        return resolveActiveCampaigns("", Integer.MAX_VALUE, Integer.MAX_VALUE).stream()
                .filter(campaign -> normalizedSlug.equals(campaign.slug()))
                .findFirst()
                .orElseThrow(() -> new ContentNotFoundException("Active campaign not found: " + normalizedSlug));
    }

    public MarketingContentModels.LegalDocument getLegalDocument(String keyOrSlug) {
        if (!enabled) {
            throw new ContentNotFoundException("Directus Marketing V2 is not enabled");
        }
        DirectusMarketingClient.DirectusLegalDocument document =
                client.fetchPublishedLegalDocument(keyOrSlug);
        return new MarketingContentModels.LegalDocument(
                document.documentKey(),
                document.slug(),
                safeUrl(document.path()),
                document.title(),
                document.summary(),
                htmlSanitizer.sanitize(document.bodyHtml()),
                document.versionLabel(),
                document.effectiveFrom(),
                document.publishedAt()
        );
    }

    public MarketingContentModels.OperationalPromotionFacts getOperationalFactsForPreview(
            String kind,
            String promotionId,
            String promoCodeId
    ) {
        DirectusMarketingClient.DirectusCampaign previewCampaign =
                new DirectusMarketingClient.DirectusCampaign(
                        null, "draft", null, null, null, null, null, null, null,
                        kind, promotionId, promoCodeId, null, null
                );
        Map<UUID, PromotionView> promotions = adminService.activePromotions().stream()
                .collect(Collectors.toMap(PromotionView::id, Function.identity(), (left, right) -> left));
        Map<UUID, PromoCodeView> promoCodes = adminService.listPromoCodes().stream()
                .filter(PromoCodeView::activeNow)
                .collect(Collectors.toMap(PromoCodeView::id, Function.identity(), (left, right) -> left));
        return operationalFacts(previewCampaign, promotions, promoCodes);
    }

    public Map<Integer, List<MarketingContentModels.Creative>> getSectionCreatives(
            List<Integer> sectionIds,
            ContentAccessMode accessMode
    ) {
        if (!enabled || sectionIds == null || sectionIds.isEmpty()) return Map.of();
        OffsetDateTime now = OffsetDateTime.now(clock);
        List<DirectusMarketingClient.DirectusSectionBanner> relations =
                client.fetchSectionBanners(sectionIds, accessMode);
        Set<String> fileIds = new LinkedHashSet<>();
        relations.forEach(relation -> {
            if (relation.banner() != null) {
                addFileId(fileIds, relation.banner().image());
                addFileId(fileIds, relation.banner().mobileImage());
            }
        });
        Map<String, DirectusContentClient.DirectusFileAsset> files = contentClient.fetchFiles(fileIds)
                .stream()
                .collect(Collectors.toMap(
                        DirectusContentClient.DirectusFileAsset::id,
                        Function.identity(),
                        (left, right) -> left
                ));
        return relations.stream()
                .filter(relation -> relation.banner() != null)
                .filter(relation -> accessMode.isPreview()
                        || isEffective(
                                relation.banner().activeFrom(),
                                relation.banner().activeTo(),
                                now
                        ))
                .collect(Collectors.groupingBy(
                        DirectusMarketingClient.DirectusSectionBanner::pageSection,
                        Collectors.mapping(
                                relation -> toCreative(relation.banner(), files),
                                Collectors.collectingAndThen(Collectors.toList(), list -> list.stream()
                                        .sorted(Comparator
                                                .comparing(
                                                        MarketingContentModels.Creative::priority,
                                                        Comparator.nullsFirst(Integer::compareTo)
                                                )
                                                .reversed()
                                                .thenComparing(
                                                        MarketingContentModels.Creative::sort,
                                                        Comparator.nullsLast(Integer::compareTo)
                                                )
                                                .thenComparing(MarketingContentModels.Creative::id))
                                        .toList())
                        )
                ));
    }

    private MarketingContentModels.Campaign toCampaign(
            DirectusMarketingClient.DirectusCampaign campaign,
            List<DirectusMarketingClient.DirectusCreative> creatives,
            Map<UUID, PromotionView> promotions,
            Map<UUID, PromoCodeView> promoCodes,
            Map<String, DirectusContentClient.DirectusFileAsset> files
    ) {
        MarketingContentModels.OperationalPromotionFacts facts = operationalFacts(
                campaign,
                promotions,
                promoCodes
        );
        if (!"none".equals(normalizeKind(campaign.operationalLinkType())) && facts == null) {
            return null;
        }
        List<MarketingContentModels.Creative> mappedCreatives = creatives.stream()
                .sorted(Comparator
                        .comparing(
                                DirectusMarketingClient.DirectusCreative::priority,
                                Comparator.nullsFirst(Integer::compareTo)
                        )
                        .reversed()
                        .thenComparing(
                                DirectusMarketingClient.DirectusCreative::sort,
                                Comparator.nullsLast(Integer::compareTo)
                        )
                        .thenComparing(DirectusMarketingClient.DirectusCreative::id))
                .map(creative -> toCreative(creative, files))
                .toList();
        return new MarketingContentModels.Campaign(
                String.valueOf(campaign.id()),
                campaign.slug(),
                campaign.internalName(),
                campaign.priority(),
                campaign.sort(),
                campaign.activeFrom(),
                campaign.activeTo(),
                publishedPageLink(campaign.landingPage()),
                publishedCollectionLink(campaign.storefrontCollection()),
                facts,
                mappedCreatives
        );
    }

    private MarketingContentModels.OperationalPromotionFacts operationalFacts(
            DirectusMarketingClient.DirectusCampaign campaign,
            Map<UUID, PromotionView> promotions,
            Map<UUID, PromoCodeView> promoCodes
    ) {
        String kind = normalizeKind(campaign.operationalLinkType());
        if (!StringUtils.hasText(kind) || "none".equals(kind)) return null;
        if ("promotion".equals(kind)) {
            PromotionView promotion = promotions.get(parseUuid(campaign.promotionId()));
            if (promotion == null || !promotion.activeNow()) return null;
            return new MarketingContentModels.OperationalPromotionFacts(
                    kind,
                    promotion.id().toString(),
                    promotion.name(),
                    null,
                    promotion.type(),
                    promotion.discountPercent(),
                    promotion.discountAmount(),
                    promotion.salePriceAmount(),
                    promotion.thresholdAmount(),
                    promotion.currency(),
                    promotion.startsAt(),
                    promotion.endsAt(),
                    promotion.description()
            );
        }
        if ("promo_code".equals(kind)) {
            PromoCodeView promoCode = promoCodes.get(parseUuid(campaign.promoCodeId()));
            if (promoCode == null || !promoCode.activeNow()) return null;
            return new MarketingContentModels.OperationalPromotionFacts(
                    kind,
                    promoCode.id().toString(),
                    null,
                    promoCode.code(),
                    "PROMO_CODE",
                    promoCode.discountPercent(),
                    promoCode.discountAmount(),
                    null,
                    promoCode.thresholdAmount(),
                    "RUB",
                    promoCode.startsAt(),
                    promoCode.endsAt(),
                    promoCode.description()
            );
        }
        return null;
    }

    private MarketingContentModels.Creative toCreative(
            DirectusMarketingClient.DirectusCreative creative,
            Map<String, DirectusContentClient.DirectusFileAsset> files
    ) {
        return new MarketingContentModels.Creative(
                String.valueOf(creative.id()),
                creative.placement(),
                creative.bannerType(),
                creative.priority(),
                creative.sort(),
                creative.shortText(),
                creative.eyebrow(),
                creative.title(),
                htmlSanitizer.sanitize(creative.description()),
                media(creative.image(), creative.imageAlt(), files),
                media(creative.mobileImage(), creative.mobileImageAlt(), files),
                creative.primaryCtaLabel(),
                safeUrl(creative.primaryCtaUrl()),
                creative.secondaryCtaLabel(),
                safeUrl(creative.secondaryCtaUrl()),
                creative.styleVariant(),
                creative.layoutVariant(),
                creative.activeFrom(),
                creative.activeTo()
        );
    }

    private ContentModels.MediaAsset media(
            String id,
            String alt,
            Map<String, DirectusContentClient.DirectusFileAsset> files
    ) {
        if (!StringUtils.hasText(id)) return null;
        DirectusContentClient.DirectusFileAsset file = files.get(id);
        return new ContentModels.MediaAsset(
                id,
                client.assetUrl(id),
                file != null ? file.width() : null,
                file != null ? file.height() : null,
                StringUtils.hasText(alt) ? alt.trim() : "",
                file != null ? file.type() : null
        );
    }

    private MarketingContentModels.PageLink publishedPageLink(
            DirectusMarketingClient.DirectusPageLink page
    ) {
        return page != null && "published".equalsIgnoreCase(page.status())
                ? new MarketingContentModels.PageLink(page.slug(), safeUrl(page.path()), page.title())
                : null;
    }

    private MarketingContentModels.StorefrontCollectionLink publishedCollectionLink(
            DirectusMarketingClient.DirectusCollectionLink collection
    ) {
        return collection != null && "published".equalsIgnoreCase(collection.status())
                ? new MarketingContentModels.StorefrontCollectionLink(collection.key(), collection.title())
                : null;
    }

    private boolean isEffective(OffsetDateTime start, OffsetDateTime end, OffsetDateTime now) {
        return (start == null || !start.isAfter(now)) && (end == null || end.isAfter(now));
    }

    private String normalizePlacement(String placement) {
        return StringUtils.hasText(placement)
                ? placement.trim().toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_')
                : "";
    }

    private String normalizeKind(String kind) {
        return StringUtils.hasText(kind) ? normalizePlacement(kind) : "none";
    }

    private int normalizeLimit(Integer limit, int maximumLimit) {
        return limit == null ? 2 : Math.max(1, Math.min(limit, maximumLimit));
    }

    private UUID parseUuid(String value) {
        if (!StringUtils.hasText(value)) return null;
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException error) {
            return null;
        }
    }

    private void addFileId(Set<String> ids, String id) {
        if (StringUtils.hasText(id)) ids.add(id.trim());
    }

    private String safeUrl(String value) {
        return htmlSanitizer.isSafeLink(value) ? value.trim() : null;
    }
}
