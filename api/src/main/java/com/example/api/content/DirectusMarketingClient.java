package com.example.api.content;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

@Component
public class DirectusMarketingClient {

    private static final ParameterizedTypeReference<ItemsResponse<DirectusCampaign>> CAMPAIGNS =
            new ParameterizedTypeReference<>() {
            };
    private static final ParameterizedTypeReference<ItemsResponse<DirectusCreative>> CREATIVES =
            new ParameterizedTypeReference<>() {
            };
    private static final ParameterizedTypeReference<ItemsResponse<DirectusLegalDocument>> LEGAL_DOCUMENTS =
            new ParameterizedTypeReference<>() {
            };
    private static final ParameterizedTypeReference<ItemsResponse<DirectusSectionFaq>> SECTION_FAQS =
            new ParameterizedTypeReference<>() {
            };
    private static final ParameterizedTypeReference<ItemsResponse<DirectusSectionLegalDocument>> SECTION_LEGAL =
            new ParameterizedTypeReference<>() {
            };
    private static final ParameterizedTypeReference<ItemsResponse<DirectusSectionBanner>> SECTION_BANNERS =
            new ParameterizedTypeReference<>() {
            };

    private final DirectusContentProperties properties;
    private final CmsObservabilityService observabilityService;
    private final RestClient restClient;

    public DirectusMarketingClient(
            RestClient.Builder restClientBuilder,
            DirectusContentProperties properties,
            CmsObservabilityService observabilityService
    ) {
        this.properties = properties;
        this.observabilityService = observabilityService;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.getConnectTimeout());
        factory.setReadTimeout(properties.getReadTimeout());
        this.restClient = restClientBuilder.requestFactory(factory).build();
    }

    public List<DirectusCampaign> fetchPublishedCampaigns() {
        MultiValueMap<String, String> query = new LinkedMultiValueMap<>();
        query.add("filter[status][_eq]", "published");
        query.add("sort", "-priority,sort,id");
        query.add("limit", "-1");
        query.add("fields", String.join(",",
                "id", "status", "published_at", "internal_name", "slug", "priority", "sort",
                "active_from", "active_to", "operational_link_type", "promotion_id", "promo_code_id",
                "landing_page.slug", "landing_page.path", "landing_page.title", "landing_page.status",
                "storefront_collection.key", "storefront_collection.title", "storefront_collection.status"
        ));
        return fetchItems("campaigns", "campaign", query, CAMPAIGNS);
    }

    public List<DirectusCreative> fetchPublishedCreatives(Collection<Integer> campaignIds, String placement) {
        if (campaignIds == null || campaignIds.isEmpty()) {
            return List.of();
        }
        MultiValueMap<String, String> query = new LinkedMultiValueMap<>();
        query.add("filter[status][_eq]", "published");
        query.add("filter[campaign][_in]", campaignIds.stream().map(String::valueOf).reduce(
                (left, right) -> left + "," + right
        ).orElse(""));
        if (StringUtils.hasText(placement)) {
            query.add("filter[placement][_eq]", normalizePlacement(placement));
        }
        query.add("sort", "-priority,sort,id");
        query.add("limit", "-1");
        query.add("fields", String.join(",",
                "id", "campaign", "status", "published_at", "internal_name", "banner_type", "placement",
                "priority", "sort", "short_text", "eyebrow", "title", "description",
                "image", "image_alt", "mobile_image", "mobile_image_alt",
                "primary_cta_label", "primary_cta_url", "secondary_cta_label", "secondary_cta_url",
                "style_variant", "layout_variant", "active_from", "active_to"
        ));
        return fetchItems("campaign_creatives", "banner", query, CREATIVES);
    }

    public DirectusLegalDocument fetchPublishedLegalDocument(String keyOrSlug) {
        String normalized = keyOrSlug == null ? "" : keyOrSlug.trim().toLowerCase(Locale.ROOT);
        if (!StringUtils.hasText(normalized)) {
            throw new IllegalArgumentException("Legal document key or slug is required");
        }
        MultiValueMap<String, String> query = new LinkedMultiValueMap<>();
        query.add("filter[status][_eq]", "published");
        query.add("filter[_or][0][document_key][_eq]", normalized);
        query.add("filter[_or][1][slug][_eq]", normalized);
        query.add("limit", "1");
        query.add("fields", String.join(",",
                "id", "document_key", "slug", "path", "title", "summary", "body_html",
                "version_label", "effective_from", "published_at"
        ));
        List<DirectusLegalDocument> documents = fetchItems(
                "legal_document",
                "legal_documents",
                query,
                LEGAL_DOCUMENTS
        );
        if (documents.isEmpty()) {
            throw new ContentNotFoundException("Published legal document not found: " + normalized);
        }
        return documents.getFirst();
    }

    public List<DirectusSectionFaq> fetchSectionFaqs(
            Collection<Integer> sectionIds,
            ContentAccessMode accessMode
    ) {
        if (sectionIds == null || sectionIds.isEmpty()) return List.of();
        MultiValueMap<String, String> query = relationQuery(
                sectionIds,
                accessMode,
                "faq",
                "page_section,sort,faq.id,faq.question,faq.answer,faq.category,faq.sort,faq.status"
        );
        return fetchItems("page_section_faqs", "page_section_faqs", query, SECTION_FAQS, accessMode);
    }

    public List<DirectusSectionLegalDocument> fetchSectionLegalDocuments(
            Collection<Integer> sectionIds,
            ContentAccessMode accessMode
    ) {
        if (sectionIds == null || sectionIds.isEmpty()) return List.of();
        MultiValueMap<String, String> query = relationQuery(
                sectionIds,
                accessMode,
                "legal_document",
                String.join(",",
                        "page_section", "sort", "legal_document.id", "legal_document.document_key",
                        "legal_document.slug", "legal_document.path", "legal_document.title",
                        "legal_document.summary", "legal_document.version_label",
                        "legal_document.effective_from", "legal_document.sort", "legal_document.status"
                )
        );
        return fetchItems(
                "page_section_legal_documents",
                "page_section_legal_documents",
                query,
                SECTION_LEGAL,
                accessMode
        );
    }

    public List<DirectusSectionBanner> fetchSectionBanners(
            Collection<Integer> sectionIds,
            ContentAccessMode accessMode
    ) {
        if (sectionIds == null || sectionIds.isEmpty()) return List.of();
        MultiValueMap<String, String> query = relationQuery(
                sectionIds,
                accessMode,
                "banner",
                String.join(",",
                        "page_section", "sort", "banner.id", "banner.status", "banner.published_at",
                        "banner.internal_name", "banner.banner_type", "banner.placement",
                        "banner.priority", "banner.sort", "banner.short_text", "banner.eyebrow",
                        "banner.title", "banner.description", "banner.image", "banner.image_alt",
                        "banner.mobile_image", "banner.mobile_image_alt",
                        "banner.primary_cta_label", "banner.primary_cta_url",
                        "banner.secondary_cta_label", "banner.secondary_cta_url",
                        "banner.style_variant", "banner.layout_variant",
                        "banner.active_from", "banner.active_to"
                )
        );
        return fetchItems(
                "page_section_banners",
                "page_section_banners",
                query,
                SECTION_BANNERS,
                accessMode
        );
    }

    public String assetUrl(String fileId) {
        if (!StringUtils.hasText(fileId)) return "";
        String baseUrl = StringUtils.hasText(properties.getPublicUrl())
                ? properties.getPublicUrl()
                : properties.getBaseUrl();
        return UriComponentsBuilder.fromHttpUrl(baseUrl.replaceAll("/+$", ""))
                .path("/assets/{id}")
                .buildAndExpand(fileId.trim())
                .encode()
                .toUriString();
    }

    private <T> List<T> fetchItems(
            String operation,
            String collection,
            MultiValueMap<String, String> query,
            ParameterizedTypeReference<ItemsResponse<T>> type
    ) {
        return fetchItems(operation, collection, query, type, ContentAccessMode.PUBLISHED);
    }

    private <T> List<T> fetchItems(
            String operation,
            String collection,
            MultiValueMap<String, String> query,
            ParameterizedTypeReference<ItemsResponse<T>> type,
            ContentAccessMode accessMode
    ) {
        URI uri = UriComponentsBuilder.fromHttpUrl(baseUrl())
                .path("/items/{collection}")
                .queryParams(query)
                .buildAndExpand(collection)
                .encode()
                .toUri();
        return observabilityService.recordDirectusRequest(operation, accessMode, uri, () -> {
            RestClient.RequestHeadersSpec<?> request = restClient.get()
                    .uri(uri)
                    .accept(MediaType.APPLICATION_JSON);
            if (StringUtils.hasText(properties.getStaticToken())) {
                request = request.header(
                        HttpHeaders.AUTHORIZATION,
                        "Bearer " + properties.getStaticToken().trim()
                );
            }
            ItemsResponse<T> response = request.retrieve().body(type);
            return response != null && response.data() != null ? response.data() : List.of();
        });
    }

    private MultiValueMap<String, String> relationQuery(
            Collection<Integer> sectionIds,
            ContentAccessMode accessMode,
            String relatedField,
            String fields
    ) {
        MultiValueMap<String, String> query = new LinkedMultiValueMap<>();
        query.add(
                "filter[page_section][_in]",
                sectionIds.stream().map(String::valueOf).reduce((left, right) -> left + "," + right).orElse("")
        );
        if (accessMode != null && accessMode.isPreview()) {
            query.add("filter[status][_neq]", "archived");
            query.add("filter[" + relatedField + "][status][_neq]", "archived");
        } else {
            query.add("filter[status][_eq]", "published");
            query.add("filter[" + relatedField + "][status][_eq]", "published");
        }
        query.add("sort", "sort,id");
        query.add("limit", "-1");
        query.add("fields", fields);
        return query;
    }

    private String baseUrl() {
        if (!StringUtils.hasText(properties.getBaseUrl())) {
            throw new IllegalStateException("DIRECTUS_BASE_URL is not configured");
        }
        return properties.getBaseUrl().replaceAll("/+$", "");
    }

    private String normalizePlacement(String placement) {
        return placement.trim().toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DirectusCampaign(
            Integer id,
            String status,
            @JsonProperty("published_at") OffsetDateTime publishedAt,
            @JsonProperty("internal_name") String internalName,
            String slug,
            Integer priority,
            Integer sort,
            @JsonProperty("active_from") OffsetDateTime activeFrom,
            @JsonProperty("active_to") OffsetDateTime activeTo,
            @JsonProperty("operational_link_type") String operationalLinkType,
            @JsonProperty("promotion_id") String promotionId,
            @JsonProperty("promo_code_id") String promoCodeId,
            @JsonProperty("landing_page") DirectusPageLink landingPage,
            @JsonProperty("storefront_collection") DirectusCollectionLink storefrontCollection
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DirectusPageLink(String slug, String path, String title, String status) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DirectusCollectionLink(String key, String title, String status) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DirectusCreative(
            Integer id,
            Integer campaign,
            String status,
            @JsonProperty("published_at") OffsetDateTime publishedAt,
            @JsonProperty("internal_name") String internalName,
            @JsonProperty("banner_type") String bannerType,
            String placement,
            Integer priority,
            Integer sort,
            @JsonProperty("short_text") String shortText,
            String eyebrow,
            String title,
            String description,
            String image,
            @JsonProperty("image_alt") String imageAlt,
            @JsonProperty("mobile_image") String mobileImage,
            @JsonProperty("mobile_image_alt") String mobileImageAlt,
            @JsonProperty("primary_cta_label") String primaryCtaLabel,
            @JsonProperty("primary_cta_url") String primaryCtaUrl,
            @JsonProperty("secondary_cta_label") String secondaryCtaLabel,
            @JsonProperty("secondary_cta_url") String secondaryCtaUrl,
            @JsonProperty("style_variant") String styleVariant,
            @JsonProperty("layout_variant") String layoutVariant,
            @JsonProperty("active_from") OffsetDateTime activeFrom,
            @JsonProperty("active_to") OffsetDateTime activeTo
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DirectusLegalDocument(
            Integer id,
            @JsonProperty("document_key") String documentKey,
            String slug,
            String path,
            String title,
            String summary,
            @JsonProperty("body_html") String bodyHtml,
            @JsonProperty("version_label") String versionLabel,
            @JsonProperty("effective_from") LocalDate effectiveFrom,
            @JsonProperty("published_at") OffsetDateTime publishedAt
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DirectusSectionFaq(
            @JsonProperty("page_section") Integer pageSection,
            Integer sort,
            DirectusFaq faq
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DirectusFaq(
            Integer id,
            String question,
            String answer,
            String category,
            Integer sort,
            String status
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DirectusSectionLegalDocument(
            @JsonProperty("page_section") Integer pageSection,
            Integer sort,
            @JsonProperty("legal_document") DirectusLegalSummary legalDocument
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DirectusSectionBanner(
            @JsonProperty("page_section") Integer pageSection,
            Integer sort,
            DirectusCreative banner
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DirectusLegalSummary(
            Integer id,
            @JsonProperty("document_key") String documentKey,
            String slug,
            String path,
            String title,
            String summary,
            @JsonProperty("version_label") String versionLabel,
            @JsonProperty("effective_from") LocalDate effectiveFrom,
            Integer sort,
            String status
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ItemsResponse<T>(List<T> data) {
    }
}
