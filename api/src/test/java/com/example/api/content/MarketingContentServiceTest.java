package com.example.api.content;

import com.example.api.admincms.DirectusAdminService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MarketingContentServiceTest {

    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-08-03T10:00:00Z");

    @Mock
    private DirectusMarketingClient marketingClient;
    @Mock
    private DirectusContentClient contentClient;
    @Mock
    private DirectusAdminService adminService;

    @Test
    void usesInclusiveStartExclusiveEndAndPriorityOrdering() {
        DirectusMarketingClient.DirectusCampaign highest = campaign(
                1, "highest", 20, NOW, NOW.plusHours(1), "none"
        );
        DirectusMarketingClient.DirectusCampaign lower = campaign(
                2, "lower", 10, NOW.minusHours(1), null, null
        );
        DirectusMarketingClient.DirectusCampaign expired = campaign(
                3, "expired", 100, NOW.minusHours(1), NOW, "none"
        );
        when(marketingClient.fetchPublishedCampaigns()).thenReturn(List.of(lower, expired, highest));
        when(marketingClient.fetchPublishedCreatives(anyCollection(), eq("home_promo")))
                .thenReturn(List.of(
                        creative(11, 1, 10, NOW, NOW.plusMinutes(30)),
                        creative(12, 2, 10, null, null),
                        creative(13, 1, 100, NOW.minusHours(1), NOW)
                ));
        when(adminService.activePromotions()).thenReturn(List.of());
        when(adminService.listPromoCodes()).thenReturn(List.of());
        when(contentClient.fetchFiles(anyCollection())).thenReturn(List.of());

        MarketingContentService service = new MarketingContentService(
                marketingClient,
                contentClient,
                adminService,
                new CmsHtmlSanitizer(),
                Clock.fixed(NOW.toInstant(), ZoneOffset.UTC),
                true
        );

        List<MarketingContentModels.Campaign> result =
                service.getActiveCampaigns("home_promo", 2);

        assertThat(result).extracting(MarketingContentModels.Campaign::slug)
                .containsExactly("highest", "lower");
        assertThat(result.getFirst().creatives()).hasSize(1);
    }

    @Test
    void suppressesCampaignWhenOperationalRecordIsNotActive() {
        when(marketingClient.fetchPublishedCampaigns()).thenReturn(List.of(
                campaign(1, "linked", 10, null, null, "promotion")
        ));
        when(marketingClient.fetchPublishedCreatives(anyCollection(), eq("home_hero")))
                .thenReturn(List.of(creative(11, 1, 10, null, null)));
        when(adminService.activePromotions()).thenReturn(List.of());
        when(adminService.listPromoCodes()).thenReturn(List.of());
        when(contentClient.fetchFiles(anyCollection())).thenReturn(List.of());

        MarketingContentService service = new MarketingContentService(
                marketingClient,
                contentClient,
                adminService,
                new CmsHtmlSanitizer(),
                Clock.fixed(NOW.toInstant(), ZoneOffset.UTC),
                true
        );

        assertThat(service.getActiveCampaigns("home_hero", 2)).isEmpty();
    }

    @Test
    void resolvesLandingCampaignBySlugBeyondPublicSlotLimit() {
        List<DirectusMarketingClient.DirectusCampaign> campaigns = IntStream.rangeClosed(1, 13)
                .mapToObj(index -> campaign(
                        index,
                        index == 13 ? "target-campaign" : "campaign-" + index,
                        10,
                        null,
                        null,
                        "none"
                ))
                .toList();
        List<DirectusMarketingClient.DirectusCreative> creatives = IntStream.rangeClosed(1, 13)
                .mapToObj(index -> creative(100 + index, index, 10, null, null))
                .toList();
        when(marketingClient.fetchPublishedCampaigns()).thenReturn(campaigns);
        when(marketingClient.fetchPublishedCreatives(anyCollection(), eq("")))
                .thenReturn(creatives);
        when(adminService.activePromotions()).thenReturn(List.of());
        when(adminService.listPromoCodes()).thenReturn(List.of());
        when(contentClient.fetchFiles(anyCollection())).thenReturn(List.of());

        MarketingContentService service = new MarketingContentService(
                marketingClient,
                contentClient,
                adminService,
                new CmsHtmlSanitizer(),
                Clock.fixed(NOW.toInstant(), ZoneOffset.UTC),
                true
        );

        assertThat(service.getActiveCampaignBySlug("target-campaign").slug())
                .isEqualTo("target-campaign");
    }

    private DirectusMarketingClient.DirectusCampaign campaign(
            int id,
            String slug,
            int priority,
            OffsetDateTime activeFrom,
            OffsetDateTime activeTo,
            String operationalKind
    ) {
        return new DirectusMarketingClient.DirectusCampaign(
                id,
                "published",
                NOW.minusDays(1),
                slug,
                slug,
                priority,
                id,
                activeFrom,
                activeTo,
                operationalKind,
                operationalKind != null && operationalKind.equals("promotion")
                        ? "11111111-1111-1111-1111-111111111111"
                        : null,
                null,
                null,
                null
        );
    }

    private DirectusMarketingClient.DirectusCreative creative(
            int id,
            int campaign,
            int priority,
            OffsetDateTime activeFrom,
            OffsetDateTime activeTo
    ) {
        return new DirectusMarketingClient.DirectusCreative(
                id,
                campaign,
                "published",
                NOW.minusDays(1),
                "creative-" + id,
                "card",
                "home_promo",
                priority,
                id,
                null,
                null,
                "Creative " + id,
                "<p>Safe copy</p>",
                null,
                null,
                null,
                null,
                "Подробнее",
                "/promo/test",
                null,
                null,
                "warm",
                "split",
                activeFrom,
                activeTo
        );
    }
}
