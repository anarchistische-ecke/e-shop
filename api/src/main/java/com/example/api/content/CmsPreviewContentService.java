package com.example.api.content;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class CmsPreviewContentService {

    private final DirectusPreviewClient previewClient;
    private final DirectusContentClient contentClient;
    private final MarketingContentService marketingContentService;
    private final CmsHtmlSanitizer htmlSanitizer;

    public CmsPreviewContentService(
            DirectusPreviewClient previewClient,
            DirectusContentClient contentClient,
            MarketingContentService marketingContentService,
            CmsHtmlSanitizer htmlSanitizer
    ) {
        this.previewClient = previewClient;
        this.contentClient = contentClient;
        this.marketingContentService = marketingContentService;
        this.htmlSanitizer = htmlSanitizer;
    }

    public PreviewTarget load(CmsPreviewTokenService.PreviewClaims claims) {
        JsonNode item = previewClient.fetchItem(
                claims.collection(),
                claims.id(),
                claims.version()
        );
        Object content = switch (claims.collection()) {
            case "page" -> mapPage(item);
            case "campaign" -> mapCampaign(item);
            case "banner" -> mapCreative(item);
            default -> throw new CmsPreviewUnauthorizedException("Unsupported preview target");
        };
        return new PreviewTarget(
                claims.collection(),
                claims.id(),
                claims.version(),
                safeReturnPath(claims.returnPath()),
                content
        );
    }

    private ContentModels.Page mapPage(JsonNode node) {
        List<ContentModels.PageSection> sections = children(node, "sections").stream()
                .map(this::unwrapRelationNode)
                .filter(JsonNode::isObject)
                .map(this::mapSection)
                .sorted(Comparator
                        .comparing(ContentModels.PageSection::sort, Comparator.nullsLast(Integer::compareTo))
                        .thenComparing(ContentModels.PageSection::internalName, Comparator.nullsLast(String::compareTo)))
                .toList();
        return new ContentModels.Page(
                text(node, "slug"),
                text(node, "path"),
                text(node, "title"),
                text(node, "template"),
                text(node, "nav_label"),
                text(node, "summary"),
                text(node, "seo_title"),
                text(node, "seo_description"),
                media(text(node, "seo_image"), ""),
                dateTime(node, "published_at"),
                sections,
                firstText(text(node, "robots"), "noindex,nofollow")
        );
    }

    private ContentModels.PageSection mapSection(JsonNode node) {
        List<ContentModels.PageSectionItem> items = children(node, "items").stream()
                .map(this::unwrapRelationNode)
                .filter(JsonNode::isObject)
                .map(this::mapSectionItem)
                .sorted(Comparator.comparing(
                        ContentModels.PageSectionItem::sort,
                        Comparator.nullsLast(Integer::compareTo)
                ))
                .toList();
        List<ContentModels.FaqItem> faqs = children(node, "faqs").stream()
                .map(entry -> relation(entry, "faq"))
                .filter(JsonNode::isObject)
                .map(faq -> new ContentModels.FaqItem(
                        text(faq, "id"),
                        text(faq, "question"),
                        htmlSanitizer.sanitize(text(faq, "answer")),
                        text(faq, "category"),
                        integer(faq, "sort")
                ))
                .toList();
        List<ContentModels.LegalDocumentSummary> legalDocuments =
                children(node, "legal_documents").stream()
                        .map(entry -> relation(entry, "legal_document"))
                        .filter(JsonNode::isObject)
                        .map(document -> new ContentModels.LegalDocumentSummary(
                                text(document, "document_key"),
                                text(document, "slug"),
                                safeUrl(text(document, "path")),
                                text(document, "title"),
                                text(document, "summary"),
                                text(document, "version_label"),
                                localDate(document, "effective_from"),
                                integer(document, "sort")
                        ))
                        .toList();
        List<MarketingContentModels.Creative> banners = children(node, "banners").stream()
                .map(entry -> relation(entry, "banner"))
                .filter(JsonNode::isObject)
                .map(this::mapCreative)
                .toList();
        String campaignPlacement = text(node, "campaign_placement");
        List<MarketingContentModels.Campaign> campaigns =
                "campaign_slot".equals(text(node, "section_type"))
                        ? marketingContentService.getActiveCampaigns(
                                campaignPlacement,
                                integer(node, "item_limit")
                        )
                        : List.of();
        return new ContentModels.PageSection(
                text(node, "internal_name"),
                text(node, "section_type"),
                integer(node, "sort"),
                text(node, "anchor_id"),
                text(node, "eyebrow"),
                text(node, "title"),
                text(node, "accent"),
                htmlSanitizer.sanitize(text(node, "body")),
                media(text(node, "image"), text(node, "image_alt")),
                media(text(node, "mobile_image"), text(node, "mobile_image_alt")),
                text(node, "primary_cta_label"),
                safeUrl(text(node, "primary_cta_url")),
                text(node, "secondary_cta_label"),
                safeUrl(text(node, "secondary_cta_url")),
                text(node, "style_variant"),
                text(node, "layout_variant"),
                dateTime(node, "published_at"),
                items,
                campaignPlacement,
                integer(node, "item_limit"),
                relationText(node, "storefront_collection", "key"),
                banners,
                faqs,
                legalDocuments,
                campaigns
        );
    }

    private ContentModels.PageSectionItem mapSectionItem(JsonNode node) {
        return new ContentModels.PageSectionItem(
                text(node, "title"),
                htmlSanitizer.sanitize(text(node, "description")),
                text(node, "label"),
                safeUrl(text(node, "url")),
                media(text(node, "image"), text(node, "image_alt")),
                text(node, "reference_kind"),
                firstText(
                        text(node, "product_key"),
                        text(node, "category_key"),
                        text(node, "brand_key"),
                        relationText(node, "storefront_collection", "key"),
                        text(node, "reference_key")
                ),
                integer(node, "sort"),
                dateTime(node, "published_at"),
                text(node, "product_key"),
                text(node, "category_key"),
                text(node, "brand_key"),
                relationText(node, "storefront_collection", "key")
        );
    }

    private MarketingContentModels.Campaign mapCampaign(JsonNode node) {
        List<MarketingContentModels.Creative> creatives = children(node, "banners").stream()
                .map(this::unwrapRelationNode)
                .filter(JsonNode::isObject)
                .map(this::mapCreative)
                .sorted(Comparator
                        .comparing(MarketingContentModels.Creative::priority, Comparator.nullsFirst(Integer::compareTo))
                        .reversed()
                        .thenComparing(MarketingContentModels.Creative::sort, Comparator.nullsLast(Integer::compareTo)))
                .toList();
        return new MarketingContentModels.Campaign(
                text(node, "id"),
                text(node, "slug"),
                text(node, "internal_name"),
                integer(node, "priority"),
                integer(node, "sort"),
                dateTime(node, "active_from"),
                dateTime(node, "active_to"),
                pageLink(node.get("landing_page")),
                collectionLink(node.get("storefront_collection")),
                marketingContentService.getOperationalFactsForPreview(
                        text(node, "operational_link_type"),
                        text(node, "promotion_id"),
                        text(node, "promo_code_id")
                ),
                creatives
        );
    }

    private MarketingContentModels.Creative mapCreative(JsonNode node) {
        return new MarketingContentModels.Creative(
                text(node, "id"),
                text(node, "placement"),
                text(node, "banner_type"),
                integer(node, "priority"),
                integer(node, "sort"),
                text(node, "short_text"),
                text(node, "eyebrow"),
                text(node, "title"),
                htmlSanitizer.sanitize(text(node, "description")),
                media(text(node, "image"), text(node, "image_alt")),
                media(text(node, "mobile_image"), text(node, "mobile_image_alt")),
                text(node, "primary_cta_label"),
                safeUrl(text(node, "primary_cta_url")),
                text(node, "secondary_cta_label"),
                safeUrl(text(node, "secondary_cta_url")),
                text(node, "style_variant"),
                text(node, "layout_variant"),
                dateTime(node, "active_from"),
                dateTime(node, "active_to")
        );
    }

    private MarketingContentModels.PageLink pageLink(JsonNode node) {
        if (node == null || !node.isObject()) return null;
        return new MarketingContentModels.PageLink(
                text(node, "slug"),
                safeUrl(text(node, "path")),
                text(node, "title")
        );
    }

    private MarketingContentModels.StorefrontCollectionLink collectionLink(JsonNode node) {
        if (node == null || !node.isObject()) return null;
        return new MarketingContentModels.StorefrontCollectionLink(
                text(node, "key"),
                text(node, "title")
        );
    }

    private ContentModels.MediaAsset media(String id, String alt) {
        if (!StringUtils.hasText(id)) return null;
        return new ContentModels.MediaAsset(
                id,
                contentClient.assetUrl(id),
                null,
                null,
                firstText(alt, ""),
                null
        );
    }

    private List<JsonNode> children(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        if (value == null || !value.isArray()) return List.of();
        List<JsonNode> result = new ArrayList<>();
        value.forEach(result::add);
        return result;
    }

    private JsonNode unwrapRelationNode(JsonNode node) {
        if (node == null || !node.isObject()) return node;
        for (String field : List.of("page_sections_id", "page_section_items_id", "banner_id")) {
            JsonNode nested = node.get(field);
            if (nested != null && nested.isObject()) return nested;
        }
        return node;
    }

    private JsonNode relation(JsonNode node, String field) {
        if (node == null || !node.isObject()) return node;
        JsonNode nested = node.get(field);
        return nested != null ? nested : node;
    }

    private String relationText(JsonNode node, String relation, String field) {
        JsonNode nested = node == null ? null : node.get(relation);
        return nested != null && nested.isObject() ? text(nested, field) : null;
    }

    private String text(JsonNode node, String field) {
        if (node == null || field == null) return null;
        JsonNode value = node.get(field);
        if (value == null || value.isNull() || value.isContainerNode()) return null;
        String result = value.asText();
        return StringUtils.hasText(result) ? result.trim() : null;
    }

    private Integer integer(JsonNode node, String field) {
        if (node == null) return null;
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) return null;
        if (value.isInt() || value.isLong()) return value.intValue();
        try {
            return Integer.valueOf(value.asText());
        } catch (NumberFormatException error) {
            return null;
        }
    }

    private OffsetDateTime dateTime(JsonNode node, String field) {
        String value = text(node, field);
        if (!StringUtils.hasText(value)) return null;
        try {
            return OffsetDateTime.parse(value);
        } catch (DateTimeParseException error) {
            return null;
        }
    }

    private LocalDate localDate(JsonNode node, String field) {
        String value = text(node, field);
        if (!StringUtils.hasText(value)) return null;
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException error) {
            return null;
        }
    }

    private String safeUrl(String value) {
        return htmlSanitizer.isSafeLink(value) ? value.trim() : null;
    }

    private String safeReturnPath(String value) {
        if (!StringUtils.hasText(value)) return "/";
        String result = value.trim();
        return result.startsWith("/") && !result.startsWith("//") ? result : "/";
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) return value.trim();
        }
        return null;
    }

    public record PreviewTarget(
            String collection,
            String id,
            String version,
            String returnPath,
            Object content
    ) {
    }
}
