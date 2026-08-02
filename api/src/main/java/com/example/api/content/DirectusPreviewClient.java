package com.example.api.content;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.Map;

@Component
public class DirectusPreviewClient {

    private static final Map<String, String> FIELDS = Map.of(
            "page", String.join(",",
                    "id", "slug", "path", "title", "template", "nav_label", "summary",
                    "seo_title", "seo_description", "seo_image", "robots", "published_at",
                    "sections.*", "sections.storefront_collection.*",
                    "sections.items.*", "sections.items.storefront_collection.*",
                    "sections.faqs.*", "sections.faqs.faq.*",
                    "sections.legal_documents.*", "sections.legal_documents.legal_document.*",
                    "sections.banners.*", "sections.banners.banner.*"
            ),
            "campaign", String.join(",",
                    "id", "internal_name", "slug", "priority", "sort", "active_from", "active_to",
                    "operational_link_type", "promotion_id", "promo_code_id",
                    "landing_page.*", "storefront_collection.*", "banners.*"
            ),
            "banner", String.join(",",
                    "id", "campaign", "internal_name", "banner_type", "placement", "priority", "sort",
                    "short_text", "eyebrow", "title", "description", "image", "image_alt",
                    "mobile_image", "mobile_image_alt", "primary_cta_label", "primary_cta_url",
                    "secondary_cta_label", "secondary_cta_url", "style_variant", "layout_variant",
                    "active_from", "active_to"
            )
    );

    private final DirectusContentProperties properties;
    private final CmsObservabilityService observabilityService;
    private final RestClient restClient;

    public DirectusPreviewClient(
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

    public JsonNode fetchItem(String collection, String id, String version) {
        String fields = FIELDS.get(collection);
        if (fields == null) {
            throw new CmsPreviewUnauthorizedException("Unsupported preview collection");
        }
        if (!StringUtils.hasText(properties.getPreviewStaticToken())) {
            throw new IllegalStateException("DIRECTUS_PREVIEW_TOKEN is required for CMS preview");
        }

        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(baseUrl())
                .path("/items/{collection}/{id}")
                .queryParam("fields", fields);
        if (StringUtils.hasText(version)) {
            builder.queryParam("version", version.trim());
        }
        URI uri = builder.buildAndExpand(collection, id).encode().toUri();

        return observabilityService.recordDirectusRequest(
                "cms_preview_" + collection,
                ContentAccessMode.PREVIEW,
                uri,
                () -> {
                    RestClient.RequestHeadersSpec<?> request = restClient.get()
                            .uri(uri)
                            .accept(MediaType.APPLICATION_JSON)
                            .header(
                                    HttpHeaders.AUTHORIZATION,
                                    "Bearer " + properties.getPreviewStaticToken().trim()
                            );
                    PreviewResponse response = request.retrieve().body(PreviewResponse.class);
                    if (response == null || response.data() == null || response.data().isNull()) {
                        throw new ContentNotFoundException("Directus preview item not found");
                    }
                    return response.data();
                }
        );
    }

    private String baseUrl() {
        if (!StringUtils.hasText(properties.getBaseUrl())) {
            throw new IllegalStateException("DIRECTUS_BASE_URL is not configured");
        }
        return properties.getBaseUrl().replaceAll("/+$", "");
    }

    private record PreviewResponse(JsonNode data) {
    }
}
