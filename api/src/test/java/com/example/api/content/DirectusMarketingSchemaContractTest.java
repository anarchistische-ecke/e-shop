package com.example.api.content;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;

class DirectusMarketingSchemaContractTest {

    private static final Map<String, Set<String>> REQUIRED_FIELDS = Map.ofEntries(
            Map.entry("site_settings", Set.of(
                    "announcement_banner", "default_og_image", "default_seo_title_suffix"
            )),
            Map.entry("banner", Set.of(
                    "campaign", "placement", "short_text", "title", "description", "image",
                    "image_alt", "mobile_image", "mobile_image_alt", "primary_cta_label",
                    "primary_cta_url", "secondary_cta_label", "secondary_cta_url",
                    "style_variant", "layout_variant", "priority", "active_from", "active_to"
            )),
            Map.entry("campaign", Set.of(
                    "status", "internal_name", "slug", "priority", "sort", "active_from",
                    "active_to", "operational_link_type", "promotion_id", "promo_code_id",
                    "landing_page", "storefront_collection", "banners"
            )),
            Map.entry("page", Set.of(
                    "slug", "path", "seo_title", "seo_description", "seo_image", "robots", "sections"
            )),
            Map.entry("page_sections", Set.of(
                    "section_type", "items", "campaign_placement", "item_limit",
                    "storefront_collection", "banners", "faqs", "legal_documents"
            )),
            Map.entry("page_section_items", Set.of(
                    "reference_kind", "reference_key", "product_key", "category_key",
                    "brand_key", "storefront_collection"
            )),
            Map.entry("navigation", Set.of("items")),
            Map.entry("navigation_items", Set.of("page", "url")),
            Map.entry("legal_documents", Set.of(
                    "document_key", "slug", "path", "body_html", "version_label", "effective_from"
            ))
    );

    @Test
    void committedSnapshotContainsEveryFieldRequestedByTheFacade() throws Exception {
        JsonNode snapshot = new ObjectMapper().readTree(Files.readString(snapshotPath()));
        Map<String, Set<String>> actual = StreamSupport.stream(
                        snapshot.path("fields").spliterator(),
                        false
                )
                .collect(Collectors.groupingBy(
                        field -> field.path("collection").asText(),
                        Collectors.mapping(field -> field.path("field").asText(), Collectors.toSet())
                ));

        REQUIRED_FIELDS.forEach((collection, expectedFields) ->
                assertThat(actual.getOrDefault(collection, Set.of()))
                        .as("Directus fields for %s", collection)
                        .containsAll(expectedFields)
        );
    }

    @Test
    void technicalCollectionsAreHiddenAndEditorCollectionsAreGrouped() throws Exception {
        JsonNode snapshot = new ObjectMapper().readTree(Files.readString(snapshotPath()));
        Map<String, JsonNode> collections = StreamSupport.stream(
                        snapshot.path("collections").spliterator(),
                        false
                )
                .collect(Collectors.toMap(node -> node.path("collection").asText(), node -> node));

        assertThat(collections.get("page_sections").path("meta").path("hidden").asBoolean()).isTrue();
        assertThat(collections.get("page_section_items").path("meta").path("hidden").asBoolean()).isTrue();
        assertThat(collections.get("post").path("meta").path("hidden").asBoolean()).isTrue();
        assertThat(collections.get("campaign").path("meta").path("group").asText())
                .isEqualTo("cms_marketing");
        assertThat(collections.get("page").path("meta").path("group").asText())
                .isEqualTo("cms_site_content");
    }

    private Path snapshotPath() {
        String multiModuleRoot = System.getProperty("maven.multiModuleProjectDirectory");
        if (multiModuleRoot != null) {
            Path path = Path.of(multiModuleRoot, "directus", "schema", "schema.snapshot.json");
            if (Files.exists(path)) return path;
        }
        Path fromModule = Path.of("..", "directus", "schema", "schema.snapshot.json")
                .toAbsolutePath()
                .normalize();
        assertThat(fromModule).exists();
        return fromModule;
    }
}
