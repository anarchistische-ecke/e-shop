package com.example.api.content;

import com.fasterxml.jackson.core.type.TypeReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DirectusContentServiceTest {

    @Mock
    private DirectusContentClient directusContentClient;

    @Mock
    private DirectusContentCacheService contentCacheService;

    private DirectusContentService service;

    @BeforeEach
    void setUp() {
        service = new DirectusContentService(directusContentClient, contentCacheService);
        lenient().when(contentCacheService.getOrLoad(
                        anyString(),
                        ArgumentMatchers.<TypeReference<Object>>any(),
                        ArgumentMatchers.<Supplier<Object>>any()
                ))
                .thenAnswer(invocation -> {
                    Supplier<?> loader = invocation.getArgument(2);
                    return loader.get();
                });
    }

    @Test
    void getNavigation_normalizesPlacementSortsGroupsAndItemsAndCachesByPlacement() {
        when(directusContentClient.fetchNavigationGroups("footer", ContentAccessMode.PUBLISHED)).thenReturn(List.of(
                new DirectusContentClient.DirectusNavigationGroup(2, "footer-service", "Service", "footer", "Service links", 20),
                new DirectusContentClient.DirectusNavigationGroup(1, "footer-company", "Company", "footer", "Company links", 10)
        ));
        when(directusContentClient.fetchNavigationItems(List.of(2, 1), ContentAccessMode.PUBLISHED)).thenReturn(List.of(
                new DirectusContentClient.DirectusNavigationItem(21, 2, "Delivery", "/info/delivery", "internal_path", false, "all", 20),
                new DirectusContentClient.DirectusNavigationItem(22, 2, "About", "/about", "internal_path", false, "all", 10),
                new DirectusContentClient.DirectusNavigationItem(11, 1, "Contacts", "/contacts", "internal_path", false, "all", 10)
        ));

        List<ContentModels.NavigationGroup> navigation = service.getNavigation(" Footer ");

        ArgumentCaptor<String> cacheKeyCaptor = ArgumentCaptor.forClass(String.class);
        verify(contentCacheService).getOrLoad(
                cacheKeyCaptor.capture(),
                ArgumentMatchers.<TypeReference<Object>>any(),
                ArgumentMatchers.<Supplier<Object>>any()
        );
        verify(directusContentClient).fetchNavigationGroups("footer", ContentAccessMode.PUBLISHED);
        verify(directusContentClient).fetchNavigationItems(List.of(2, 1), ContentAccessMode.PUBLISHED);

        assertThat(cacheKeyCaptor.getValue()).isEqualTo("navigation:footer");
        assertThat(navigation).extracting(ContentModels.NavigationGroup::key)
                .containsExactly("footer-company", "footer-service");
        assertThat(navigation.get(1).items()).extracting(ContentModels.NavigationItem::label)
                .containsExactly("About", "Delivery");
    }

    @Test
    void getPreviewNavigation_bypassesCacheAndUsesPreviewAccessMode() {
        when(directusContentClient.fetchNavigationGroups("header", ContentAccessMode.PREVIEW)).thenReturn(List.of(
                new DirectusContentClient.DirectusNavigationGroup(7, "header-main", "Main", "header", null, 1)
        ));
        when(directusContentClient.fetchNavigationItems(List.of(7), ContentAccessMode.PREVIEW)).thenReturn(List.of(
                new DirectusContentClient.DirectusNavigationItem(70, 7, "Catalog", "/catalog", "internal_path", false, "all", 1)
        ));

        List<ContentModels.NavigationGroup> navigation = service.getPreviewNavigation(" header ");

        verifyNoInteractions(contentCacheService);
        verify(directusContentClient).fetchNavigationGroups("header", ContentAccessMode.PREVIEW);
        verify(directusContentClient).fetchNavigationItems(List.of(7), ContentAccessMode.PREVIEW);
        assertThat(navigation).hasSize(1);
        assertThat(navigation.getFirst().items()).extracting(ContentModels.NavigationItem::label)
                .containsExactly("Catalog");
    }

    @Test
    void getPageBySlug_mapsMediaSortsSectionsAndUsesNormalizedCacheKey() {
        OffsetDateTime publishedAt = OffsetDateTime.parse("2026-04-12T10:15:30Z");

        when(directusContentClient.fetchPageBySlug("delivery", ContentAccessMode.PUBLISHED)).thenReturn(
                new DirectusContentClient.DirectusPage(
                        101,
                        "delivery",
                        "/info/delivery",
                        "Delivery",
                        "content",
                        "Delivery",
                        "Delivery summary",
                        "Delivery SEO",
                        "SEO description",
                        "seo-asset",
                        publishedAt
                )
        );
        when(directusContentClient.fetchPageSections(101, ContentAccessMode.PUBLISHED)).thenReturn(List.of(
                new DirectusContentClient.DirectusPageSection(
                        2, 101, "Body", "rich_text", 20, null, null, "Delivery body", null,
                        null, null, null, null, null, null, null, null, null, "default", "contained", publishedAt
                ),
                new DirectusContentClient.DirectusPageSection(
                        1, 101, "Hero", "hero", 10, "hero", "Fast", "Delivery",
                        "Same week", "Hero copy", "hero-image", "Hero alt override",
                        "hero-mobile", null, "Shop now", "/catalog", null, null, "warm", "full", publishedAt
                )
        ));
        when(directusContentClient.fetchPageSectionItems(List.of(2, 1), ContentAccessMode.PUBLISHED)).thenReturn(List.of(
                new DirectusContentClient.DirectusPageSectionItem(22, 1, "Guarantee", "Ships protected", null, null, "card-image", null, null, null, 20, publishedAt),
                new DirectusContentClient.DirectusPageSectionItem(21, 1, "Natural fabrics", "Premium cotton", null, null, "card-image-2", "Card alt override", null, null, 10, publishedAt)
        ));
        when(directusContentClient.fetchFiles(any())).thenReturn(List.of(
                new DirectusContentClient.DirectusFileAsset("seo-asset", "SEO share", null, 1200, 630, "seo.jpg", "image/jpeg"),
                new DirectusContentClient.DirectusFileAsset("hero-image", "Hero title", "Hero description", 1600, 900, "hero.jpg", "image/jpeg"),
                new DirectusContentClient.DirectusFileAsset("card-image", "Guarantee title", "Ships safely", 640, 480, "card.jpg", "image/jpeg"),
                new DirectusContentClient.DirectusFileAsset("card-image-2", "Fallback title", "Fallback description", 320, 240, "card-2.jpg", "image/jpeg")
        ));
        when(directusContentClient.assetUrl(anyString())).thenAnswer(invocation -> "http://cms.test/assets/" + invocation.getArgument(0));

        ContentModels.Page page = service.getPageBySlug(" Delivery ");

        ArgumentCaptor<String> cacheKeyCaptor = ArgumentCaptor.forClass(String.class);
        verify(contentCacheService).getOrLoad(
                cacheKeyCaptor.capture(),
                ArgumentMatchers.<TypeReference<Object>>any(),
                ArgumentMatchers.<Supplier<Object>>any()
        );
        verify(directusContentClient).fetchPageBySlug("delivery", ContentAccessMode.PUBLISHED);
        verify(directusContentClient).fetchPageSections(101, ContentAccessMode.PUBLISHED);
        verify(directusContentClient).fetchPageSectionItems(List.of(2, 1), ContentAccessMode.PUBLISHED);

        assertThat(cacheKeyCaptor.getValue()).isEqualTo("page:delivery");
        assertThat(page.slug()).isEqualTo("delivery");
        assertThat(page.seoImage().url()).isEqualTo("http://cms.test/assets/seo-asset");
        assertThat(page.seoImage().alt()).isEqualTo("SEO share");
        assertThat(page.sections()).extracting(ContentModels.PageSection::internalName)
                .containsExactly("Hero", "Body");
        assertThat(page.sections().getFirst().image().alt()).isEqualTo("Hero alt override");
        assertThat(page.sections().getFirst().mobileImage().url()).isEqualTo("http://cms.test/assets/hero-mobile");
        assertThat(page.sections().getFirst().mobileImage().width()).isNull();
        assertThat(page.sections().getFirst().items()).extracting(ContentModels.PageSectionItem::title)
                .containsExactly("Natural fabrics", "Guarantee");
        assertThat(page.sections().getFirst().items().getFirst().image().alt()).isEqualTo("Card alt override");
        assertThat(page.sections().getFirst().items().get(1).image().alt()).isEqualTo("Ships safely");
    }

    @Test
    void getSiteSettings_returnsUrlOnlyMediaWhenFileLookupFails() {
        OffsetDateTime publishedAt = OffsetDateTime.parse("2026-04-12T09:00:00Z");

        when(directusContentClient.fetchSiteSettings(ContentAccessMode.PUBLISHED)).thenReturn(
                new DirectusContentClient.DirectusSiteSettings(
                        "Cozyhome",
                        "Brand description",
                        "+7 (999) 111-22-33",
                        "support@example.com",
                        "Cozyhome",
                        "Cozyhome LLC",
                        "1234567890",
                        "0987654321",
                        "Moscow",
                        2015,
                        "Cozyhome",
                        "Fallback SEO",
                        "og-image",
                        "published",
                        publishedAt
                )
        );
        when(directusContentClient.fetchFiles(any())).thenThrow(new IllegalStateException("Directus files unavailable"));
        when(directusContentClient.assetUrl("og-image")).thenReturn("http://cms.test/assets/og-image");

        ContentModels.SiteSettings settings = service.getSiteSettings();

        ArgumentCaptor<String> cacheKeyCaptor = ArgumentCaptor.forClass(String.class);
        verify(contentCacheService).getOrLoad(
                cacheKeyCaptor.capture(),
                ArgumentMatchers.<TypeReference<Object>>any(),
                ArgumentMatchers.<Supplier<Object>>any()
        );
        verify(directusContentClient).fetchSiteSettings(ContentAccessMode.PUBLISHED);

        assertThat(cacheKeyCaptor.getValue()).isEqualTo("site-settings");
        assertThat(settings.defaultOgImage()).isNotNull();
        assertThat(settings.defaultOgImage().url()).isEqualTo("http://cms.test/assets/og-image");
        assertThat(settings.defaultOgImage().width()).isNull();
        assertThat(settings.defaultOgImage().alt()).isEmpty();
        assertThat(settings.publishedAt()).isEqualTo(publishedAt);
    }
}
