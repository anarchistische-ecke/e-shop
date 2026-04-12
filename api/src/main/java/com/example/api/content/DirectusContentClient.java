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
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class DirectusContentClient {

    private static final ParameterizedTypeReference<DirectusItemsResponse<DirectusNavigationGroup>> NAVIGATION_GROUPS_RESPONSE =
            new ParameterizedTypeReference<>() {
            };
    private static final ParameterizedTypeReference<DirectusItemsResponse<DirectusNavigationItem>> NAVIGATION_ITEMS_RESPONSE =
            new ParameterizedTypeReference<>() {
            };
    private static final ParameterizedTypeReference<DirectusItemsResponse<DirectusPage>> PAGES_RESPONSE =
            new ParameterizedTypeReference<>() {
            };
    private static final ParameterizedTypeReference<DirectusItemsResponse<DirectusPageSection>> PAGE_SECTIONS_RESPONSE =
            new ParameterizedTypeReference<>() {
            };
    private static final ParameterizedTypeReference<DirectusItemsResponse<DirectusPageSectionItem>> PAGE_SECTION_ITEMS_RESPONSE =
            new ParameterizedTypeReference<>() {
            };
    private static final ParameterizedTypeReference<DirectusItemsResponse<Map<String, Object>>> FILES_RESPONSE =
            new ParameterizedTypeReference<>() {
            };

    private final DirectusContentProperties properties;
    private final RestClient restClient;

    public DirectusContentClient(RestClient.Builder restClientBuilder, DirectusContentProperties properties) {
        this.properties = properties;
        this.restClient = restClientBuilder
                .requestFactory(requestFactory(properties))
                .build();
    }

    public DirectusSiteSettings fetchSiteSettings() {
        return fetchSiteSettings(ContentAccessMode.PUBLISHED);
    }

    public DirectusSiteSettings fetchSiteSettings(ContentAccessMode accessMode) {
        URI uri = UriComponentsBuilder.fromHttpUrl(baseUrl())
                .path("/items/site_settings")
                .queryParam("fields", String.join(",",
                        "site_name",
                        "brand_description",
                        "support_phone",
                        "support_email",
                        "legal_entity_short",
                        "legal_entity_full",
                        "legal_inn",
                        "legal_ogrnip",
                        "legal_address",
                        "copyright_start_year",
                        "default_seo_title_suffix",
                        "default_seo_description",
                        "default_og_image",
                        "status",
                        "published_at"
                ))
                .build(true)
                .toUri();

        DirectusSingletonResponse<DirectusSiteSettings> response = request(uri)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });

        if (response == null || response.data() == null || !statusMatches(response.data().status(), accessMode)) {
            throw new ContentNotFoundException(contentNotFoundMessage("site settings", accessMode));
        }

        return response.data();
    }

    public List<DirectusNavigationGroup> fetchNavigationGroups(String placement) {
        return fetchNavigationGroups(placement, ContentAccessMode.PUBLISHED);
    }

    public List<DirectusNavigationGroup> fetchNavigationGroups(String placement, ContentAccessMode accessMode) {
        MultiValueMap<String, String> query = new LinkedMultiValueMap<>();
        applyStatusFilter(query, accessMode);
        if (StringUtils.hasText(placement)) {
            query.add("filter[placement][_eq]", placement.trim().toLowerCase(Locale.ROOT));
        }
        query.add("sort", "sort,key");
        query.add("limit", "-1");
        query.add("fields", "id,key,title,placement,description,sort");

        DirectusItemsResponse<DirectusNavigationGroup> response = request(itemsUri("navigation", query))
                .retrieve()
                .body(NAVIGATION_GROUPS_RESPONSE);

        return response != null && response.data() != null ? response.data() : List.of();
    }

    public List<DirectusNavigationItem> fetchNavigationItems(List<Integer> navigationIds) {
        return fetchNavigationItems(navigationIds, ContentAccessMode.PUBLISHED);
    }

    public List<DirectusNavigationItem> fetchNavigationItems(List<Integer> navigationIds, ContentAccessMode accessMode) {
        if (navigationIds == null || navigationIds.isEmpty()) {
            return List.of();
        }

        MultiValueMap<String, String> query = new LinkedMultiValueMap<>();
        applyStatusFilter(query, accessMode);
        query.add("filter[navigation][_in]", joinIntegerIds(navigationIds));
        query.add("sort", "sort,id");
        query.add("limit", "-1");
        query.add("fields", "id,navigation,label,url,item_type,open_in_new_tab,visibility,sort");

        DirectusItemsResponse<DirectusNavigationItem> response = request(itemsUri("navigation_items", query))
                .retrieve()
                .body(NAVIGATION_ITEMS_RESPONSE);

        return response != null && response.data() != null ? response.data() : List.of();
    }

    public DirectusPage fetchPageBySlug(String slug) {
        return fetchPageBySlug(slug, ContentAccessMode.PUBLISHED);
    }

    public DirectusPage fetchPageBySlug(String slug, ContentAccessMode accessMode) {
        MultiValueMap<String, String> query = new LinkedMultiValueMap<>();
        query.add("filter[slug][_eq]", slug);
        applyStatusFilter(query, accessMode);
        query.add("limit", "1");
        query.add("fields", "id,slug,path,title,template,nav_label,summary,seo_title,seo_description,seo_image,published_at");

        DirectusItemsResponse<DirectusPage> response = request(itemsUri("page", query))
                .retrieve()
                .body(PAGES_RESPONSE);

        if (response == null || response.data() == null || response.data().isEmpty()) {
            throw new ContentNotFoundException(contentNotFoundMessage("page", accessMode) + " for slug: " + slug);
        }

        return response.data().getFirst();
    }

    public List<DirectusPageSection> fetchPageSections(int pageId) {
        return fetchPageSections(pageId, ContentAccessMode.PUBLISHED);
    }

    public List<DirectusPageSection> fetchPageSections(int pageId, ContentAccessMode accessMode) {
        MultiValueMap<String, String> query = new LinkedMultiValueMap<>();
        query.add("filter[page][_eq]", Integer.toString(pageId));
        applyStatusFilter(query, accessMode);
        query.add("sort", "sort,id");
        query.add("limit", "-1");
        query.add("fields", String.join(",",
                "id",
                "page",
                "internal_name",
                "section_type",
                "sort",
                "anchor_id",
                "eyebrow",
                "title",
                "accent",
                "body",
                "image",
                "image_alt",
                "mobile_image",
                "mobile_image_alt",
                "primary_cta_label",
                "primary_cta_url",
                "secondary_cta_label",
                "secondary_cta_url",
                "style_variant",
                "layout_variant",
                "published_at"
        ));

        DirectusItemsResponse<DirectusPageSection> response = request(itemsUri("page_sections", query))
                .retrieve()
                .body(PAGE_SECTIONS_RESPONSE);

        return response != null && response.data() != null ? response.data() : List.of();
    }

    public List<DirectusPageSectionItem> fetchPageSectionItems(List<Integer> sectionIds) {
        return fetchPageSectionItems(sectionIds, ContentAccessMode.PUBLISHED);
    }

    public List<DirectusPageSectionItem> fetchPageSectionItems(List<Integer> sectionIds, ContentAccessMode accessMode) {
        if (sectionIds == null || sectionIds.isEmpty()) {
            return List.of();
        }

        MultiValueMap<String, String> query = new LinkedMultiValueMap<>();
        query.add("filter[page_section][_in]", joinIntegerIds(sectionIds));
        applyStatusFilter(query, accessMode);
        query.add("sort", "sort,id");
        query.add("limit", "-1");
        query.add("fields", "id,page_section,title,description,label,url,image,image_alt,reference_kind,reference_key,sort,published_at");

        DirectusItemsResponse<DirectusPageSectionItem> response = request(itemsUri("page_section_items", query))
                .retrieve()
                .body(PAGE_SECTION_ITEMS_RESPONSE);

        return response != null && response.data() != null ? response.data() : List.of();
    }

    public List<DirectusFileAsset> fetchFiles(Collection<String> fileIds) {
        if (fileIds == null || fileIds.isEmpty()) {
            return List.of();
        }

        List<String> normalizedIds = fileIds.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .toList();

        if (normalizedIds.isEmpty()) {
            return List.of();
        }

        MultiValueMap<String, String> query = new LinkedMultiValueMap<>();
        query.add("filter[id][_in]", joinValues(normalizedIds));
        query.add("limit", "-1");
        query.add("fields", "id,title,description,width,height,filename_download,type");

        URI uri = UriComponentsBuilder.fromHttpUrl(baseUrl())
                .path("/files")
                .queryParams(query)
                .build()
                .encode()
                .toUri();

        DirectusItemsResponse<Map<String, Object>> response = request(uri)
                .retrieve()
                .body(FILES_RESPONSE);

        if (response == null || response.data() == null) {
            return List.of();
        }

        return response.data().stream()
                .map(file -> new DirectusFileAsset(
                        asText(file.get("id")),
                        asText(file.get("title")),
                        asText(file.get("description")),
                        asInteger(file.get("width")),
                        asInteger(file.get("height")),
                        asText(file.get("filename_download")),
                        asText(file.get("type"))
                ))
                .filter(file -> StringUtils.hasText(file.id()))
                .toList();
    }

    public String assetUrl(String fileId) {
        if (!StringUtils.hasText(fileId)) {
            return "";
        }

        return UriComponentsBuilder.fromHttpUrl(baseUrl())
                .path("/assets/{id}")
                .buildAndExpand(fileId.trim())
                .encode()
                .toUriString();
    }

    private void applyStatusFilter(MultiValueMap<String, String> query, ContentAccessMode accessMode) {
        if (accessMode != null && accessMode.isPreview()) {
            query.add("filter[status][_neq]", "archived");
            return;
        }

        query.add("filter[status][_eq]", "published");
    }

    private boolean statusMatches(String status, ContentAccessMode accessMode) {
        if (accessMode != null && accessMode.isPreview()) {
            return status != null && !"archived".equalsIgnoreCase(status);
        }

        return "published".equalsIgnoreCase(status);
    }

    private String contentNotFoundMessage(String resourceName, ContentAccessMode accessMode) {
        if (accessMode != null && accessMode.isPreview()) {
            return "Preview " + resourceName + " not found in Directus";
        }
        return "Published " + resourceName + " not found in Directus";
    }

    private RestClient.RequestHeadersSpec<?> request(URI uri) {
        RestClient.RequestHeadersSpec<?> request = restClient.get()
                .uri(uri)
                .accept(MediaType.APPLICATION_JSON);

        if (StringUtils.hasText(properties.getStaticToken())) {
            request = request.header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getStaticToken().trim());
        }

        return request;
    }

    private URI itemsUri(String collection, MultiValueMap<String, String> query) {
        return UriComponentsBuilder.fromHttpUrl(baseUrl())
                .path("/items/{collection}")
                .queryParams(query)
                .buildAndExpand(collection)
                .encode()
                .toUri();
    }

    private String baseUrl() {
        if (!StringUtils.hasText(properties.getBaseUrl())) {
            throw new ResourceAccessException("DIRECTUS_BASE_URL is not configured");
        }

        return properties.getBaseUrl().replaceAll("/+$", "");
    }

    private static SimpleClientHttpRequestFactory requestFactory(DirectusContentProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.getConnectTimeout());
        factory.setReadTimeout(properties.getReadTimeout());
        return factory;
    }

    private static String joinIntegerIds(List<Integer> ids) {
        return ids.stream()
                .map(String::valueOf)
                .reduce((left, right) -> left + "," + right)
                .orElse("");
    }

    private static String joinValues(Collection<String> values) {
        return values.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .reduce((left, right) -> left + "," + right)
                .orElse("");
    }

    private static String asText(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static Integer asInteger(Object value) {
        if (value == null) {
            return null;
        }

        if (value instanceof Number numericValue) {
            return numericValue.intValue();
        }

        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException error) {
            return null;
        }
    }

    private record DirectusSingletonResponse<T>(T data) {
    }

    private record DirectusItemsResponse<T>(List<T> data) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DirectusSiteSettings(
            @JsonProperty("site_name") String siteName,
            @JsonProperty("brand_description") String brandDescription,
            @JsonProperty("support_phone") String supportPhone,
            @JsonProperty("support_email") String supportEmail,
            @JsonProperty("legal_entity_short") String legalEntityShort,
            @JsonProperty("legal_entity_full") String legalEntityFull,
            @JsonProperty("legal_inn") String legalInn,
            @JsonProperty("legal_ogrnip") String legalOgrnip,
            @JsonProperty("legal_address") String legalAddress,
            @JsonProperty("copyright_start_year") Integer copyrightStartYear,
            @JsonProperty("default_seo_title_suffix") String defaultSeoTitleSuffix,
            @JsonProperty("default_seo_description") String defaultSeoDescription,
            @JsonProperty("default_og_image") String defaultOgImage,
            String status,
            @JsonProperty("published_at") OffsetDateTime publishedAt
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DirectusNavigationGroup(
            Integer id,
            String key,
            String title,
            String placement,
            String description,
            Integer sort
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DirectusNavigationItem(
            Integer id,
            Integer navigation,
            String label,
            String url,
            @JsonProperty("item_type") String itemType,
            @JsonProperty("open_in_new_tab") Boolean openInNewTab,
            String visibility,
            Integer sort
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DirectusPage(
            Integer id,
            String slug,
            String path,
            String title,
            String template,
            @JsonProperty("nav_label") String navLabel,
            String summary,
            @JsonProperty("seo_title") String seoTitle,
            @JsonProperty("seo_description") String seoDescription,
            @JsonProperty("seo_image") String seoImage,
            @JsonProperty("published_at") OffsetDateTime publishedAt
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DirectusPageSection(
            Integer id,
            Integer page,
            @JsonProperty("internal_name") String internalName,
            @JsonProperty("section_type") String sectionType,
            Integer sort,
            @JsonProperty("anchor_id") String anchorId,
            String eyebrow,
            String title,
            String accent,
            String body,
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
            @JsonProperty("published_at") OffsetDateTime publishedAt
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DirectusPageSectionItem(
            Integer id,
            @JsonProperty("page_section") Integer pageSection,
            String title,
            String description,
            String label,
            String url,
            String image,
            @JsonProperty("image_alt") String imageAlt,
            @JsonProperty("reference_kind") String referenceKind,
            @JsonProperty("reference_key") String referenceKey,
            Integer sort,
            @JsonProperty("published_at") OffsetDateTime publishedAt
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DirectusFileAsset(
            String id,
            String title,
            String description,
            Integer width,
            Integer height,
            @JsonProperty("filename_download") String filenameDownload,
            String type
    ) {
    }
}
