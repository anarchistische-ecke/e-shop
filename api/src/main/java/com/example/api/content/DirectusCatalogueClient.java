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
import java.util.Map;

@Component
public class DirectusCatalogueClient {

    private static final ParameterizedTypeReference<DirectusItemsResponse<DirectusOverlayRecord>> OVERLAYS_RESPONSE =
            new ParameterizedTypeReference<>() {
            };
    private static final ParameterizedTypeReference<DirectusItemsResponse<DirectusOverlayBlock>> OVERLAY_BLOCKS_RESPONSE =
            new ParameterizedTypeReference<>() {
            };
    private static final ParameterizedTypeReference<DirectusItemsResponse<DirectusOverlayBlockItem>> OVERLAY_BLOCK_ITEMS_RESPONSE =
            new ParameterizedTypeReference<>() {
            };
    private static final ParameterizedTypeReference<DirectusItemsResponse<DirectusStorefrontCollection>> STOREFRONT_COLLECTIONS_RESPONSE =
            new ParameterizedTypeReference<>() {
            };
    private static final ParameterizedTypeReference<DirectusItemsResponse<DirectusStorefrontCollectionItem>> STOREFRONT_COLLECTION_ITEMS_RESPONSE =
            new ParameterizedTypeReference<>() {
            };
    private static final ParameterizedTypeReference<DirectusItemsResponse<Map<String, Object>>> FILES_RESPONSE =
            new ParameterizedTypeReference<>() {
            };

    private final DirectusContentProperties properties;
    private final CmsObservabilityService observabilityService;
    private final RestClient restClient;

    public DirectusCatalogueClient(
            RestClient.Builder restClientBuilder,
            DirectusContentProperties properties,
            CmsObservabilityService observabilityService
    ) {
        this.properties = properties;
        this.observabilityService = observabilityService;
        this.restClient = restClientBuilder
                .requestFactory(requestFactory(properties))
                .build();
    }

    public List<DirectusOverlayRecord> fetchProductOverlays(Collection<String> productKeys, ContentAccessMode accessMode) {
        return fetchOverlayRecords("product_overlay", "product_key", productKeys, accessMode, "product_overlays");
    }

    public List<DirectusOverlayRecord> fetchCategoryOverlays(Collection<String> categoryKeys, ContentAccessMode accessMode) {
        return fetchOverlayRecords("category_overlay", "category_key", categoryKeys, accessMode, "category_overlays");
    }

    public List<DirectusOverlayBlock> fetchOverlayBlocks(String ownerKind, Collection<String> ownerKeys, ContentAccessMode accessMode) {
        if (!StringUtils.hasText(ownerKind) || ownerKeys == null || ownerKeys.isEmpty()) {
            return List.of();
        }

        List<String> normalizedOwnerKeys = normalizeValues(ownerKeys);
        if (normalizedOwnerKeys.isEmpty()) {
            return List.of();
        }

        MultiValueMap<String, String> query = new LinkedMultiValueMap<>();
        query.add("filter[owner_kind][_eq]", ownerKind.trim().toLowerCase());
        query.add("filter[owner_key][_in]", joinValues(normalizedOwnerKeys));
        applyStatusFilter(query, accessMode);
        query.add("sort", "sort,id");
        query.add("limit", "-1");
        query.add("fields", String.join(",",
                "id",
                "owner_kind",
                "owner_key",
                "internal_name",
                "block_type",
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
                "collection_key",
                "status",
                "published_at"
        ));

        URI uri = itemsUri("catalogue_overlay_block", query);
        return observe("catalogue_overlay_blocks", accessMode, uri, () -> {
            DirectusItemsResponse<DirectusOverlayBlock> response = request(uri)
                    .retrieve()
                    .body(OVERLAY_BLOCKS_RESPONSE);

            return response != null && response.data() != null ? response.data() : List.of();
        });
    }

    public List<DirectusOverlayBlockItem> fetchOverlayBlockItems(Collection<Integer> blockIds, ContentAccessMode accessMode) {
        if (blockIds == null || blockIds.isEmpty()) {
            return List.of();
        }

        List<Integer> normalizedIds = blockIds.stream()
                .filter(id -> id != null && id > 0)
                .distinct()
                .toList();

        if (normalizedIds.isEmpty()) {
            return List.of();
        }

        MultiValueMap<String, String> query = new LinkedMultiValueMap<>();
        query.add("filter[overlay_block][_in]", joinIntegerIds(normalizedIds));
        applyStatusFilter(query, accessMode);
        query.add("sort", "sort,id");
        query.add("limit", "-1");
        query.add("fields", String.join(",",
                "id",
                "overlay_block",
                "title",
                "description",
                "label",
                "url",
                "image",
                "image_alt",
                "reference_kind",
                "reference_key",
                "behavior",
                "sort",
                "status",
                "published_at"
        ));

        URI uri = itemsUri("catalogue_overlay_block_item", query);
        return observe("catalogue_overlay_block_items", accessMode, uri, () -> {
            DirectusItemsResponse<DirectusOverlayBlockItem> response = request(uri)
                    .retrieve()
                    .body(OVERLAY_BLOCK_ITEMS_RESPONSE);

            return response != null && response.data() != null ? response.data() : List.of();
        });
    }

    public DirectusStorefrontCollection fetchStorefrontCollection(String key, ContentAccessMode accessMode) {
        String normalizedKey = normalizeSingleToken(key, "Storefront collection key is required");
        MultiValueMap<String, String> query = new LinkedMultiValueMap<>();
        query.add("filter[key][_eq]", normalizedKey);
        applyStatusFilter(query, accessMode);
        query.add("limit", "1");
        query.add("fields", String.join(",",
                "id",
                "key",
                "title",
                "description",
                "mode",
                "rule_type",
                "category_key",
                "brand_key",
                "limit",
                "sort_mode",
                "seo_title",
                "seo_description",
                "seo_image",
                "hero_eyebrow",
                "hero_title",
                "hero_accent",
                "hero_body",
                "hero_image",
                "hero_image_alt",
                "hero_mobile_image",
                "hero_mobile_image_alt",
                "hero_primary_cta_label",
                "hero_primary_cta_url",
                "hero_secondary_cta_label",
                "hero_secondary_cta_url",
                "hero_style_variant",
                "hero_layout_variant",
                "primary_cta_label",
                "primary_cta_url",
                "status",
                "published_at"
        ));

        URI uri = itemsUri("storefront_collection", query);
        return observe("storefront_collection", accessMode, uri, () -> {
            DirectusItemsResponse<DirectusStorefrontCollection> response = request(uri)
                    .retrieve()
                    .body(STOREFRONT_COLLECTIONS_RESPONSE);

            if (response == null || response.data() == null || response.data().isEmpty()) {
                throw new ContentNotFoundException(contentNotFoundMessage("storefront collection", accessMode) + " for key: " + normalizedKey);
            }

            return response.data().getFirst();
        });
    }

    public List<DirectusStorefrontCollectionItem> fetchStorefrontCollectionItems(int collectionId, ContentAccessMode accessMode) {
        if (collectionId <= 0) {
            return List.of();
        }

        MultiValueMap<String, String> query = new LinkedMultiValueMap<>();
        query.add("filter[storefront_collection][_eq]", Integer.toString(collectionId));
        applyStatusFilter(query, accessMode);
        query.add("sort", "sort,id");
        query.add("limit", "-1");
        query.add("fields", "id,storefront_collection,entity_kind,entity_key,behavior,sort,status,published_at");

        URI uri = itemsUri("storefront_collection_item", query);
        return observe("storefront_collection_items", accessMode, uri, () -> {
            DirectusItemsResponse<DirectusStorefrontCollectionItem> response = request(uri)
                    .retrieve()
                    .body(STOREFRONT_COLLECTION_ITEMS_RESPONSE);

            return response != null && response.data() != null ? response.data() : List.of();
        });
    }

    public List<DirectusFileAsset> fetchFiles(Collection<String> fileIds) {
        if (fileIds == null || fileIds.isEmpty()) {
            return List.of();
        }

        List<String> normalizedIds = normalizeValues(fileIds);
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

        return observe("catalogue_files", null, uri, () -> {
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
        });
    }

    public String assetUrl(String fileId) {
        if (!StringUtils.hasText(fileId)) {
            return "";
        }

        return UriComponentsBuilder.fromHttpUrl(assetBaseUrl())
                .path("/assets/{id}")
                .buildAndExpand(fileId.trim())
                .encode()
                .toUriString();
    }

    private List<DirectusOverlayRecord> fetchOverlayRecords(
            String collection,
            String keyField,
            Collection<String> keys,
            ContentAccessMode accessMode,
            String operation
    ) {
        if (keys == null || keys.isEmpty()) {
            return List.of();
        }

        List<String> normalizedKeys = normalizeValues(keys);
        if (normalizedKeys.isEmpty()) {
            return List.of();
        }

        MultiValueMap<String, String> query = new LinkedMultiValueMap<>();
        query.add("filter[" + keyField + "][_in]", joinValues(normalizedKeys));
        applyStatusFilter(query, accessMode);
        query.add("limit", "-1");
        query.add("fields", String.join(",",
                "id",
                keyField,
                "seo_title",
                "seo_description",
                "seo_image",
                "badge_text",
                "ribbon_text",
                "marketing_title",
                "marketing_subtitle",
                "intro_body",
                "hero_eyebrow",
                "hero_title",
                "hero_accent",
                "hero_body",
                "hero_image",
                "hero_image_alt",
                "hero_mobile_image",
                "hero_mobile_image_alt",
                "hero_primary_cta_label",
                "hero_primary_cta_url",
                "hero_secondary_cta_label",
                "hero_secondary_cta_url",
                "hero_style_variant",
                "hero_layout_variant",
                "linked_collection_keys",
                "status",
                "published_at"
        ));

        URI uri = itemsUri(collection, query);
        return observe(operation, accessMode, uri, () -> {
            DirectusItemsResponse<DirectusOverlayRecord> response = request(uri)
                    .retrieve()
                    .body(OVERLAYS_RESPONSE);

            return response != null && response.data() != null ? response.data() : List.of();
        });
    }

    private void applyStatusFilter(MultiValueMap<String, String> query, ContentAccessMode accessMode) {
        if (accessMode != null && accessMode.isPreview()) {
            query.add("filter[status][_neq]", "archived");
            return;
        }

        query.add("filter[status][_eq]", "published");
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

    private <T> T observe(String operation, ContentAccessMode accessMode, URI uri, java.util.function.Supplier<T> supplier) {
        return observabilityService.recordDirectusRequest(operation, accessMode, uri, supplier);
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

    private String assetBaseUrl() {
        if (StringUtils.hasText(properties.getPublicUrl())) {
            return properties.getPublicUrl().replaceAll("/+$", "");
        }

        return baseUrl();
    }

    private static List<String> normalizeValues(Collection<String> values) {
        return values.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .toList();
    }

    private static String normalizeSingleToken(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(message);
        }

        return value.trim().toLowerCase();
    }

    private static String joinValues(Collection<String> values) {
        return values.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .reduce((left, right) -> left + "," + right)
                .orElse("");
    }

    private static String joinIntegerIds(Collection<Integer> ids) {
        return ids.stream()
                .map(String::valueOf)
                .reduce((left, right) -> left + "," + right)
                .orElse("");
    }

    private static SimpleClientHttpRequestFactory requestFactory(DirectusContentProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.getConnectTimeout());
        factory.setReadTimeout(properties.getReadTimeout());
        return factory;
    }

    private static String asText(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static Integer asInteger(Object value) {
        if (value == null) {
            return null;
        }

        if (value instanceof Number number) {
            return number.intValue();
        }

        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException error) {
            return null;
        }
    }

    private record DirectusItemsResponse<T>(List<T> data) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DirectusOverlayRecord(
            Integer id,
            @JsonProperty("product_key") String productKey,
            @JsonProperty("category_key") String categoryKey,
            @JsonProperty("seo_title") String seoTitle,
            @JsonProperty("seo_description") String seoDescription,
            @JsonProperty("seo_image") String seoImage,
            @JsonProperty("badge_text") String badgeText,
            @JsonProperty("ribbon_text") String ribbonText,
            @JsonProperty("marketing_title") String marketingTitle,
            @JsonProperty("marketing_subtitle") String marketingSubtitle,
            @JsonProperty("intro_body") String introBody,
            @JsonProperty("hero_eyebrow") String heroEyebrow,
            @JsonProperty("hero_title") String heroTitle,
            @JsonProperty("hero_accent") String heroAccent,
            @JsonProperty("hero_body") String heroBody,
            @JsonProperty("hero_image") String heroImage,
            @JsonProperty("hero_image_alt") String heroImageAlt,
            @JsonProperty("hero_mobile_image") String heroMobileImage,
            @JsonProperty("hero_mobile_image_alt") String heroMobileImageAlt,
            @JsonProperty("hero_primary_cta_label") String heroPrimaryCtaLabel,
            @JsonProperty("hero_primary_cta_url") String heroPrimaryCtaUrl,
            @JsonProperty("hero_secondary_cta_label") String heroSecondaryCtaLabel,
            @JsonProperty("hero_secondary_cta_url") String heroSecondaryCtaUrl,
            @JsonProperty("hero_style_variant") String heroStyleVariant,
            @JsonProperty("hero_layout_variant") String heroLayoutVariant,
            @JsonProperty("linked_collection_keys") Object linkedCollectionKeys,
            String status,
            @JsonProperty("published_at") OffsetDateTime publishedAt
    ) {
        public String overlayKey() {
            return StringUtils.hasText(productKey) ? productKey : categoryKey;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DirectusOverlayBlock(
            Integer id,
            @JsonProperty("owner_kind") String ownerKind,
            @JsonProperty("owner_key") String ownerKey,
            @JsonProperty("internal_name") String internalName,
            @JsonProperty("block_type") String blockType,
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
            @JsonProperty("collection_key") String collectionKey,
            String status,
            @JsonProperty("published_at") OffsetDateTime publishedAt
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DirectusOverlayBlockItem(
            Integer id,
            @JsonProperty("overlay_block") Integer overlayBlock,
            String title,
            String description,
            String label,
            String url,
            String image,
            @JsonProperty("image_alt") String imageAlt,
            @JsonProperty("reference_kind") String referenceKind,
            @JsonProperty("reference_key") String referenceKey,
            String behavior,
            Integer sort,
            String status,
            @JsonProperty("published_at") OffsetDateTime publishedAt
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DirectusStorefrontCollection(
            Integer id,
            String key,
            String title,
            String description,
            String mode,
            @JsonProperty("rule_type") String ruleType,
            @JsonProperty("category_key") String categoryKey,
            @JsonProperty("brand_key") String brandKey,
            Integer limit,
            @JsonProperty("sort_mode") String sortMode,
            @JsonProperty("seo_title") String seoTitle,
            @JsonProperty("seo_description") String seoDescription,
            @JsonProperty("seo_image") String seoImage,
            @JsonProperty("hero_eyebrow") String heroEyebrow,
            @JsonProperty("hero_title") String heroTitle,
            @JsonProperty("hero_accent") String heroAccent,
            @JsonProperty("hero_body") String heroBody,
            @JsonProperty("hero_image") String heroImage,
            @JsonProperty("hero_image_alt") String heroImageAlt,
            @JsonProperty("hero_mobile_image") String heroMobileImage,
            @JsonProperty("hero_mobile_image_alt") String heroMobileImageAlt,
            @JsonProperty("hero_primary_cta_label") String heroPrimaryCtaLabel,
            @JsonProperty("hero_primary_cta_url") String heroPrimaryCtaUrl,
            @JsonProperty("hero_secondary_cta_label") String heroSecondaryCtaLabel,
            @JsonProperty("hero_secondary_cta_url") String heroSecondaryCtaUrl,
            @JsonProperty("hero_style_variant") String heroStyleVariant,
            @JsonProperty("hero_layout_variant") String heroLayoutVariant,
            @JsonProperty("primary_cta_label") String primaryCtaLabel,
            @JsonProperty("primary_cta_url") String primaryCtaUrl,
            String status,
            @JsonProperty("published_at") OffsetDateTime publishedAt
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DirectusStorefrontCollectionItem(
            Integer id,
            @JsonProperty("storefront_collection") Integer storefrontCollection,
            @JsonProperty("entity_kind") String entityKind,
            @JsonProperty("entity_key") String entityKey,
            String behavior,
            Integer sort,
            String status,
            @JsonProperty("published_at") OffsetDateTime publishedAt
    ) {
    }

    public record DirectusFileAsset(
            String id,
            String title,
            String description,
            Integer width,
            Integer height,
            String filename,
            String type
    ) {
    }
}
