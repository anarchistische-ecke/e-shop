package com.example.api.content;

import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class DirectusContentService implements ContentService {

    private static final Logger log = LoggerFactory.getLogger(DirectusContentService.class);

    private static final TypeReference<ContentModels.SiteSettings> SITE_SETTINGS_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<List<ContentModels.NavigationGroup>> NAVIGATION_GROUPS_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<ContentModels.Page> PAGE_TYPE = new TypeReference<>() {
    };

    private final DirectusContentClient directusContentClient;
    private final DirectusContentCacheService contentCacheService;
    private final DirectusMarketingClient directusMarketingClient;
    private final MarketingContentService marketingContentService;
    private final CmsHtmlSanitizer htmlSanitizer;

    public DirectusContentService(
            DirectusContentClient directusContentClient,
            DirectusContentCacheService contentCacheService
    ) {
        this(
                directusContentClient,
                contentCacheService,
                null,
                null,
                new CmsHtmlSanitizer()
        );
    }

    @Autowired
    public DirectusContentService(
            DirectusContentClient directusContentClient,
            DirectusContentCacheService contentCacheService,
            DirectusMarketingClient directusMarketingClient,
            MarketingContentService marketingContentService,
            CmsHtmlSanitizer htmlSanitizer
    ) {
        this.directusContentClient = directusContentClient;
        this.contentCacheService = contentCacheService;
        this.directusMarketingClient = directusMarketingClient;
        this.marketingContentService = marketingContentService;
        this.htmlSanitizer = htmlSanitizer;
    }

    @Override
    public ContentModels.SiteSettings getSiteSettings() {
        ContentModels.SiteSettings settings = contentCacheService.getOrLoad(
                ContentCacheKeys.siteSettings(),
                SITE_SETTINGS_TYPE,
                () -> {
            return loadSiteSettings(ContentAccessMode.PUBLISHED);
        });
        ContentModels.AnnouncementBanner campaignAnnouncement = activeCampaignAnnouncement();
        if (campaignAnnouncement == null) return settings;
        return new ContentModels.SiteSettings(
                settings.siteName(),
                settings.brandDescription(),
                settings.supportPhone(),
                settings.supportEmail(),
                settings.legalEntityShort(),
                settings.legalEntityFull(),
                settings.legalInn(),
                settings.legalOgrnip(),
                settings.legalAddress(),
                settings.copyrightStartYear(),
                settings.defaultSeoTitleSuffix(),
                settings.defaultSeoDescription(),
                settings.defaultOgImage(),
                campaignAnnouncement,
                settings.publishedAt()
        );
    }

    @Override
    public ContentModels.SiteSettings getPreviewSiteSettings() {
        return loadSiteSettings(ContentAccessMode.PREVIEW);
    }

    @Override
    public List<ContentModels.NavigationGroup> getNavigation(String placement) {
        String normalizedPlacement = normalizePlacement(placement);
        String cacheKey = normalizedPlacement == null
                ? ContentCacheKeys.navigationAll()
                : ContentCacheKeys.navigation(normalizedPlacement);

        return contentCacheService.getOrLoad(cacheKey, NAVIGATION_GROUPS_TYPE, () -> {
            return loadNavigation(normalizedPlacement, ContentAccessMode.PUBLISHED);
        });
    }

    @Override
    public List<ContentModels.NavigationGroup> getPreviewNavigation(String placement) {
        return loadNavigation(normalizePlacement(placement), ContentAccessMode.PREVIEW);
    }

    @Override
    public ContentModels.Page getPageBySlug(String slug) {
        if (!StringUtils.hasText(slug)) {
            throw new IllegalArgumentException("Page slug is required");
        }

        String normalizedSlug = slug.trim().toLowerCase(Locale.ROOT);
        ContentModels.Page page = contentCacheService.getOrLoad(
                ContentCacheKeys.page(normalizedSlug),
                PAGE_TYPE,
                () -> {
            return loadPage(normalizedSlug, ContentAccessMode.PUBLISHED);
        });
        return resolveCampaignSlots(page);
    }

    @Override
    public ContentModels.Page getPreviewPageBySlug(String slug) {
        if (!StringUtils.hasText(slug)) {
            throw new IllegalArgumentException("Page slug is required");
        }

        return loadPage(slug.trim().toLowerCase(Locale.ROOT), ContentAccessMode.PREVIEW);
    }

    private ContentModels.SiteSettings loadSiteSettings(ContentAccessMode accessMode) {
        var item = directusContentClient.fetchSiteSettings(accessMode);
        Set<String> fileIds = new LinkedHashSet<>();
        addFileId(fileIds, item.defaultOgImage());
        Map<String, DirectusContentClient.DirectusFileAsset> fileAssetsById = loadFileAssets(fileIds);

        ContentModels.AnnouncementBanner announcement =
                toAnnouncementBanner(item.announcementBanner(), accessMode);

        return new ContentModels.SiteSettings(
                item.siteName(),
                item.brandDescription(),
                item.supportPhone(),
                item.supportEmail(),
                item.legalEntityShort(),
                item.legalEntityFull(),
                item.legalInn(),
                item.legalOgrnip(),
                item.legalAddress(),
                item.copyrightStartYear(),
                item.defaultSeoTitleSuffix(),
                item.defaultSeoDescription(),
                toMediaAsset(item.defaultOgImage(), null, fileAssetsById),
                announcement,
                item.publishedAt()
        );
    }

    private ContentModels.AnnouncementBanner activeCampaignAnnouncement() {
        if (marketingContentService == null) return null;
        try {
            return marketingContentService
                    .getActiveCampaigns("sitewide_announcement", 1)
                    .stream()
                    .findFirst()
                    .flatMap(campaign -> campaign.creatives().stream().findFirst())
                    .map(creative -> new ContentModels.AnnouncementBanner(
                            creative.id(),
                            firstText(creative.shortText(), creative.title()),
                            creative.styleVariant(),
                            creative.primaryCtaLabel(),
                            creative.primaryCtaUrl(),
                            creative.secondaryCtaLabel(),
                            creative.secondaryCtaUrl(),
                            creative.activeFrom(),
                            creative.activeTo(),
                            OffsetDateTime.now()
                    ))
                    .orElse(null);
        } catch (RuntimeException error) {
            log.warn("Failed to resolve active site announcement campaign", error);
            return null;
        }
    }

    private ContentModels.AnnouncementBanner toAnnouncementBanner(
            DirectusContentClient.DirectusBanner banner,
            ContentAccessMode accessMode
    ) {
        if (!isBannerVisible(banner, accessMode)) {
            return null;
        }

        return new ContentModels.AnnouncementBanner(
                banner.id(),
                banner.shortText(),
                banner.styleVariant(),
                banner.primaryCtaLabel(),
                safeUrl(banner.primaryCtaUrl()),
                banner.secondaryCtaLabel(),
                safeUrl(banner.secondaryCtaUrl()),
                banner.activeFrom(),
                banner.activeTo(),
                banner.publishedAt()
        );
    }

    private boolean isBannerVisible(DirectusContentClient.DirectusBanner banner, ContentAccessMode accessMode) {
        if (banner == null || !StringUtils.hasText(banner.shortText())) {
            return false;
        }

        if (accessMode != null && accessMode.isPreview()) {
            return banner.status() == null || !"archived".equalsIgnoreCase(banner.status());
        }

        if (!"published".equalsIgnoreCase(banner.status())) {
            return false;
        }

        OffsetDateTime now = OffsetDateTime.now();
        if (banner.activeFrom() != null && banner.activeFrom().isAfter(now)) {
            return false;
        }
        return banner.activeTo() == null || banner.activeTo().isAfter(now);
    }

    private List<ContentModels.NavigationGroup> loadNavigation(String placement, ContentAccessMode accessMode) {
        var groups = directusContentClient.fetchNavigationGroups(placement, accessMode);
        var itemsByNavigationId = directusContentClient.fetchNavigationItems(
                        groups.stream()
                                .map(DirectusContentClient.DirectusNavigationGroup::id)
                                .filter(id -> id != null && id > 0)
                                .toList(),
                        accessMode
                ).stream()
                .collect(Collectors.groupingBy(
                        DirectusContentClient.DirectusNavigationItem::navigation,
                        Collectors.mapping(
                                item -> new ContentModels.NavigationItem(
                                        item.label(),
                                        firstText(
                                                safeUrl(item.url()),
                                                item.page() != null ? safeUrl(item.page().path()) : null
                                        ),
                                        item.itemType(),
                                        item.openInNewTab(),
                                        item.visibility(),
                                        item.sort(),
                                        item.page() != null ? item.page().slug() : null,
                                        item.page() != null ? item.page().path() : null
                                ),
                                Collectors.collectingAndThen(Collectors.toList(), list -> list.stream()
                                        .sorted(Comparator.comparing(ContentModels.NavigationItem::sort, Comparator.nullsLast(Integer::compareTo))
                                                .thenComparing(ContentModels.NavigationItem::label, Comparator.nullsLast(String::compareToIgnoreCase)))
                                        .toList())
                        )
                ));

        return groups.stream()
                .sorted(Comparator.comparing(DirectusContentClient.DirectusNavigationGroup::sort, Comparator.nullsLast(Integer::compareTo))
                        .thenComparing(DirectusContentClient.DirectusNavigationGroup::key, Comparator.nullsLast(String::compareToIgnoreCase)))
                .map(group -> new ContentModels.NavigationGroup(
                        group.key(),
                        group.title(),
                        group.placement(),
                        group.description(),
                        group.sort(),
                        itemsByNavigationId.getOrDefault(group.id(), List.of())
                ))
                .toList();
    }

    private ContentModels.Page loadPage(String slug, ContentAccessMode accessMode) {
        var page = directusContentClient.fetchPageBySlug(slug, accessMode);
        var sections = directusContentClient.fetchPageSections(page.id(), accessMode);
        var sectionItems = directusContentClient.fetchPageSectionItems(
                        sections.stream()
                                .map(DirectusContentClient.DirectusPageSection::id)
                                .filter(id -> id != null && id > 0)
                                .toList(),
                        accessMode
                );
        List<Integer> sectionIds = sections.stream()
                .map(DirectusContentClient.DirectusPageSection::id)
                .filter(id -> id != null && id > 0)
                .toList();
        Map<Integer, List<ContentModels.FaqItem>> faqsBySection = loadSectionFaqs(
                sectionIds,
                accessMode
        );
        Map<Integer, List<ContentModels.LegalDocumentSummary>> legalBySection =
                loadSectionLegalDocuments(sectionIds, accessMode);
        Map<Integer, List<MarketingContentModels.Creative>> bannersBySection =
                loadSectionBanners(sectionIds, accessMode);

        Set<String> fileIds = new LinkedHashSet<>();
        addFileId(fileIds, page.seoImage());
        sections.forEach(section -> {
            addFileId(fileIds, section.image());
            addFileId(fileIds, section.mobileImage());
        });
        sectionItems.forEach(item -> addFileId(fileIds, item.image()));

        Map<String, DirectusContentClient.DirectusFileAsset> fileAssetsById = loadFileAssets(fileIds);

        var itemsBySectionId = sectionItems.stream()
                .collect(Collectors.groupingBy(
                        DirectusContentClient.DirectusPageSectionItem::pageSection,
                        Collectors.mapping(
                                item -> new ContentModels.PageSectionItem(
                                        item.title(),
                                        htmlSanitizer.sanitize(item.description()),
                                        item.label(),
                                        safeUrl(item.url()),
                                        toMediaAsset(item.image(), item.imageAlt(), fileAssetsById),
                                        resolvedReferenceKind(item),
                                        resolvedReferenceKey(item),
                                        item.sort(),
                                        item.publishedAt(),
                                        item.productKey(),
                                        item.categoryKey(),
                                        item.brandKey(),
                                        publishedCollectionKey(item.storefrontCollection())
                                ),
                                Collectors.collectingAndThen(Collectors.toList(), list -> list.stream()
                                        .sorted(Comparator.comparing(ContentModels.PageSectionItem::sort, Comparator.nullsLast(Integer::compareTo))
                                                .thenComparing(ContentModels.PageSectionItem::title, Comparator.nullsLast(String::compareToIgnoreCase)))
                                        .toList())
                        )
                ));

        List<ContentModels.PageSection> sectionModels = sections.stream()
                .sorted(Comparator.comparing(DirectusContentClient.DirectusPageSection::sort, Comparator.nullsLast(Integer::compareTo))
                        .thenComparing(DirectusContentClient.DirectusPageSection::id, Comparator.nullsLast(Integer::compareTo)))
                .map(section -> new ContentModels.PageSection(
                        section.internalName(),
                        section.sectionType(),
                        section.sort(),
                        section.anchorId(),
                        section.eyebrow(),
                        section.title(),
                        section.accent(),
                        htmlSanitizer.sanitize(section.body()),
                        toMediaAsset(section.image(), section.imageAlt(), fileAssetsById),
                        toMediaAsset(section.mobileImage(), section.mobileImageAlt(), fileAssetsById),
                        section.primaryCtaLabel(),
                        safeUrl(section.primaryCtaUrl()),
                        section.secondaryCtaLabel(),
                        safeUrl(section.secondaryCtaUrl()),
                        section.styleVariant(),
                        section.layoutVariant(),
                        section.publishedAt(),
                        itemsBySectionId.getOrDefault(section.id(), List.of()),
                        section.campaignPlacement(),
                        section.itemLimit(),
                        publishedCollectionKey(section.storefrontCollection()),
                        bannersBySection.getOrDefault(section.id(), List.of()),
                        faqsBySection.getOrDefault(section.id(), List.of()),
                        legalBySection.getOrDefault(section.id(), List.of()),
                        List.of()
                ))
                .toList();

        return new ContentModels.Page(
                page.slug(),
                page.path(),
                page.title(),
                page.template(),
                page.navLabel(),
                page.summary(),
                page.seoTitle(),
                page.seoDescription(),
                toMediaAsset(page.seoImage(), null, fileAssetsById),
                page.publishedAt(),
                sectionModels,
                firstText(page.robots(), "index,follow")
        );
    }

    private Map<Integer, List<ContentModels.FaqItem>> loadSectionFaqs(
            List<Integer> sectionIds,
            ContentAccessMode accessMode
    ) {
        if (directusMarketingClient == null || sectionIds.isEmpty()) return Map.of();
        return directusMarketingClient.fetchSectionFaqs(sectionIds, accessMode).stream()
                .filter(relation -> relation.faq() != null)
                .collect(Collectors.groupingBy(
                        DirectusMarketingClient.DirectusSectionFaq::pageSection,
                        Collectors.mapping(
                                relation -> new ContentModels.FaqItem(
                                        String.valueOf(relation.faq().id()),
                                        relation.faq().question(),
                                        htmlSanitizer.sanitize(relation.faq().answer()),
                                        relation.faq().category(),
                                        relation.sort() != null ? relation.sort() : relation.faq().sort()
                                ),
                                Collectors.collectingAndThen(Collectors.toList(), List::copyOf)
                        )
                ));
    }

    private Map<Integer, List<ContentModels.LegalDocumentSummary>> loadSectionLegalDocuments(
            List<Integer> sectionIds,
            ContentAccessMode accessMode
    ) {
        if (directusMarketingClient == null || sectionIds.isEmpty()) return Map.of();
        return directusMarketingClient.fetchSectionLegalDocuments(sectionIds, accessMode).stream()
                .filter(relation -> relation.legalDocument() != null)
                .collect(Collectors.groupingBy(
                        DirectusMarketingClient.DirectusSectionLegalDocument::pageSection,
                        Collectors.mapping(
                                relation -> {
                                    DirectusMarketingClient.DirectusLegalSummary document =
                                            relation.legalDocument();
                                    return new ContentModels.LegalDocumentSummary(
                                            document.documentKey(),
                                            document.slug(),
                                            document.path(),
                                            document.title(),
                                            document.summary(),
                                            document.versionLabel(),
                                            document.effectiveFrom(),
                                            relation.sort() != null ? relation.sort() : document.sort()
                                    );
                                },
                                Collectors.collectingAndThen(Collectors.toList(), List::copyOf)
                        )
                ));
    }

    private Map<Integer, List<MarketingContentModels.Creative>> loadSectionBanners(
            List<Integer> sectionIds,
            ContentAccessMode accessMode
    ) {
        if (marketingContentService == null || sectionIds.isEmpty()) return Map.of();
        try {
            return marketingContentService.getSectionCreatives(sectionIds, accessMode);
        } catch (RuntimeException error) {
            log.warn("Failed to resolve banner groups for {} page section(s)", sectionIds.size(), error);
            return Map.of();
        }
    }

    private ContentModels.Page resolveCampaignSlots(ContentModels.Page page) {
        if (page == null || page.sections() == null || marketingContentService == null) return page;
        boolean hasCampaignSlots = page.sections().stream()
                .anyMatch(section -> "campaign_slot".equalsIgnoreCase(section.sectionType()));
        if (!hasCampaignSlots) return page;
        List<ContentModels.PageSection> sections = page.sections().stream()
                .map(section -> new ContentModels.PageSection(
                        section.internalName(),
                        section.sectionType(),
                        section.sort(),
                        section.anchorId(),
                        section.eyebrow(),
                        section.title(),
                        section.accent(),
                        section.body(),
                        section.image(),
                        section.mobileImage(),
                        section.primaryCtaLabel(),
                        section.primaryCtaUrl(),
                        section.secondaryCtaLabel(),
                        section.secondaryCtaUrl(),
                        section.styleVariant(),
                        section.layoutVariant(),
                        section.publishedAt(),
                        section.items(),
                        section.campaignPlacement(),
                        section.itemLimit(),
                        section.storefrontCollectionKey(),
                        section.banners(),
                        section.faqs(),
                        section.legalDocuments(),
                        activeCampaignsForSection(section)
                ))
                .toList();
        return new ContentModels.Page(
                page.slug(),
                page.path(),
                page.title(),
                page.template(),
                page.navLabel(),
                page.summary(),
                page.seoTitle(),
                page.seoDescription(),
                page.seoImage(),
                page.publishedAt(),
                sections,
                page.robots()
        );
    }

    private List<MarketingContentModels.Campaign> activeCampaignsForSection(
            ContentModels.PageSection section
    ) {
        if (!"campaign_slot".equalsIgnoreCase(section.sectionType())) {
            return section.campaigns() != null ? section.campaigns() : List.of();
        }
        try {
            return marketingContentService.getActiveCampaigns(
                    section.campaignPlacement(),
                    section.itemLimit()
            );
        } catch (RuntimeException error) {
            log.warn("Failed to resolve campaign slot {}", section.internalName(), error);
            return List.of();
        }
    }

    private String resolvedReferenceKind(DirectusContentClient.DirectusPageSectionItem item) {
        if (StringUtils.hasText(item.productKey())) return "product_slug";
        if (StringUtils.hasText(item.categoryKey())) return "category_slug";
        if (StringUtils.hasText(item.brandKey())) return "brand_slug";
        if (StringUtils.hasText(publishedCollectionKey(item.storefrontCollection()))) {
            return "storefront_collection";
        }
        return item.referenceKind();
    }

    private String resolvedReferenceKey(DirectusContentClient.DirectusPageSectionItem item) {
        return firstText(
                item.productKey(),
                item.categoryKey(),
                item.brandKey(),
                publishedCollectionKey(item.storefrontCollection()),
                item.referenceKey()
        );
    }

    private String publishedCollectionKey(
            DirectusContentClient.DirectusCollectionReference collection
    ) {
        return collection != null && "published".equalsIgnoreCase(collection.status())
                ? collection.key()
                : null;
    }

    private String safeUrl(String value) {
        return htmlSanitizer.isSafeLink(value) ? value.trim() : null;
    }

    private Map<String, DirectusContentClient.DirectusFileAsset> loadFileAssets(Iterable<String> fileIds) {
        if (fileIds == null) {
            return Map.of();
        }

        Set<String> normalizedIds = new LinkedHashSet<>();
        fileIds.forEach(fileId -> addFileId(normalizedIds, fileId));

        if (normalizedIds.isEmpty()) {
            return Map.of();
        }

        try {
            return directusContentClient.fetchFiles(normalizedIds).stream()
                    .filter(asset -> StringUtils.hasText(asset.id()))
                    .collect(Collectors.toMap(
                            DirectusContentClient.DirectusFileAsset::id,
                            Function.identity(),
                            (left, right) -> left
                    ));
        } catch (RuntimeException error) {
            log.warn("Failed to fetch Directus file metadata for {} asset(s). Returning URL-only media payloads.", normalizedIds.size(), error);
            return Map.of();
        }
    }

    private ContentModels.MediaAsset toMediaAsset(
            String fileId,
            String altOverride,
            Map<String, DirectusContentClient.DirectusFileAsset> fileAssetsById
    ) {
        if (!StringUtils.hasText(fileId)) {
            return null;
        }

        String normalizedFileId = fileId.trim();
        DirectusContentClient.DirectusFileAsset fileAsset = fileAssetsById.get(normalizedFileId);

        return new ContentModels.MediaAsset(
                normalizedFileId,
                directusContentClient.assetUrl(normalizedFileId),
                fileAsset != null ? fileAsset.width() : null,
                fileAsset != null ? fileAsset.height() : null,
                firstText(
                        altOverride,
                        fileAsset != null ? fileAsset.description() : null,
                        fileAsset != null ? fileAsset.title() : null
                ),
                fileAsset != null ? fileAsset.type() : null
        );
    }

    private static void addFileId(Set<String> fileIds, String fileId) {
        if (!StringUtils.hasText(fileId)) {
            return;
        }

        fileIds.add(fileId.trim());
    }

    private static String firstText(String... values) {
        if (values == null) {
            return "";
        }

        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }

        return "";
    }

    private String normalizePlacement(String placement) {
        if (!StringUtils.hasText(placement)) {
            return null;
        }

        return placement.trim().toLowerCase(Locale.ROOT);
    }
}
