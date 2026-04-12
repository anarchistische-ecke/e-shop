package com.example.api.content;

import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Service
public class DirectusContentService implements ContentService {

    private static final TypeReference<ContentModels.SiteSettings> SITE_SETTINGS_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<List<ContentModels.NavigationGroup>> NAVIGATION_GROUPS_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<ContentModels.Page> PAGE_TYPE = new TypeReference<>() {
    };

    private final DirectusContentClient directusContentClient;
    private final DirectusContentCacheService contentCacheService;

    public DirectusContentService(
            DirectusContentClient directusContentClient,
            DirectusContentCacheService contentCacheService
    ) {
        this.directusContentClient = directusContentClient;
        this.contentCacheService = contentCacheService;
    }

    @Override
    public ContentModels.SiteSettings getSiteSettings() {
        return contentCacheService.getOrLoad(ContentCacheKeys.siteSettings(), SITE_SETTINGS_TYPE, () -> {
            return loadSiteSettings(ContentAccessMode.PUBLISHED);
        });
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
        return contentCacheService.getOrLoad(ContentCacheKeys.page(normalizedSlug), PAGE_TYPE, () -> {
            return loadPage(normalizedSlug, ContentAccessMode.PUBLISHED);
        });
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
                item.publishedAt()
        );
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
                                        item.url(),
                                        item.itemType(),
                                        item.openInNewTab(),
                                        item.visibility(),
                                        item.sort()
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
        var itemsBySectionId = directusContentClient.fetchPageSectionItems(
                        sections.stream()
                                .map(DirectusContentClient.DirectusPageSection::id)
                                .filter(id -> id != null && id > 0)
                                .toList(),
                        accessMode
                ).stream()
                .collect(Collectors.groupingBy(
                        DirectusContentClient.DirectusPageSectionItem::pageSection,
                        Collectors.mapping(
                                item -> new ContentModels.PageSectionItem(
                                        item.title(),
                                        item.description(),
                                        item.label(),
                                        item.url(),
                                        item.referenceKind(),
                                        item.referenceKey(),
                                        item.sort(),
                                        item.publishedAt()
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
                        section.body(),
                        section.primaryCtaLabel(),
                        section.primaryCtaUrl(),
                        section.secondaryCtaLabel(),
                        section.secondaryCtaUrl(),
                        section.styleVariant(),
                        section.layoutVariant(),
                        section.publishedAt(),
                        itemsBySectionId.getOrDefault(section.id(), List.of())
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
                page.publishedAt(),
                sectionModels
        );
    }

    private String normalizePlacement(String placement) {
        if (!StringUtils.hasText(placement)) {
            return null;
        }

        return placement.trim().toLowerCase(Locale.ROOT);
    }
}
