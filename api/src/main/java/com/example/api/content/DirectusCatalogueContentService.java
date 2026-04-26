package com.example.api.content;

import com.fasterxml.jackson.core.type.TypeReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class DirectusCatalogueContentService implements CatalogueContentService {

    private static final Logger log = LoggerFactory.getLogger(DirectusCatalogueContentService.class);

    private static final TypeReference<CatalogueContentModels.StorefrontCollectionDefinition> STOREFRONT_COLLECTION_DEFINITION_TYPE =
            new TypeReference<>() {
            };

    private final DirectusCatalogueClient directusCatalogueClient;
    private final DirectusContentCacheService contentCacheService;

    public DirectusCatalogueContentService(
            DirectusCatalogueClient directusCatalogueClient,
            DirectusContentCacheService contentCacheService
    ) {
        this.directusCatalogueClient = directusCatalogueClient;
        this.contentCacheService = contentCacheService;
    }

    @Override
    public Map<String, CatalogueContentModels.CatalogueOverlay> getPublishedProductOverlays(Collection<String> productKeys) {
        return loadOverlays("product", normalizeKeys(productKeys), ContentAccessMode.PUBLISHED);
    }

    @Override
    public Map<String, CatalogueContentModels.CatalogueOverlay> getPreviewProductOverlays(Collection<String> productKeys) {
        return loadOverlays("product", normalizeKeys(productKeys), ContentAccessMode.PREVIEW);
    }

    @Override
    public Map<String, CatalogueContentModels.CatalogueOverlay> getPublishedCategoryOverlays(Collection<String> categoryKeys) {
        return loadOverlays("category", normalizeKeys(categoryKeys), ContentAccessMode.PUBLISHED);
    }

    @Override
    public Map<String, CatalogueContentModels.CatalogueOverlay> getPreviewCategoryOverlays(Collection<String> categoryKeys) {
        return loadOverlays("category", normalizeKeys(categoryKeys), ContentAccessMode.PREVIEW);
    }

    @Override
    public CatalogueContentModels.StorefrontCollectionDefinition getStorefrontCollection(String key) {
        String normalizedKey = normalizeRequiredKey(key, "Storefront collection key is required");
        return contentCacheService.getOrLoad(
                ContentCacheKeys.collection(normalizedKey),
                STOREFRONT_COLLECTION_DEFINITION_TYPE,
                () -> loadStorefrontCollectionDefinition(normalizedKey, ContentAccessMode.PUBLISHED)
        );
    }

    @Override
    public CatalogueContentModels.StorefrontCollectionDefinition getPreviewStorefrontCollection(String key) {
        return loadStorefrontCollectionDefinition(
                normalizeRequiredKey(key, "Storefront collection key is required"),
                ContentAccessMode.PREVIEW
        );
    }

    private Map<String, CatalogueContentModels.CatalogueOverlay> loadOverlays(
            String entityKind,
            List<String> normalizedKeys,
            ContentAccessMode accessMode
    ) {
        if (normalizedKeys.isEmpty()) {
            return Map.of();
        }

        List<DirectusCatalogueClient.DirectusOverlayRecord> overlays = "category".equals(entityKind)
                ? directusCatalogueClient.fetchCategoryOverlays(normalizedKeys, accessMode)
                : directusCatalogueClient.fetchProductOverlays(normalizedKeys, accessMode);

        if (overlays.isEmpty()) {
            return Map.of();
        }

        List<DirectusCatalogueClient.DirectusOverlayBlock> blocks = directusCatalogueClient.fetchOverlayBlocks(entityKind, normalizedKeys, accessMode);
        List<DirectusCatalogueClient.DirectusOverlayBlockItem> blockItems = directusCatalogueClient.fetchOverlayBlockItems(
                blocks.stream()
                        .map(DirectusCatalogueClient.DirectusOverlayBlock::id)
                        .filter(id -> id != null && id > 0)
                        .toList(),
                accessMode
        );

        Set<String> fileIds = new LinkedHashSet<>();
        overlays.forEach(overlay -> {
            addFileId(fileIds, overlay.seoImage());
            addFileId(fileIds, overlay.heroImage());
            addFileId(fileIds, overlay.heroMobileImage());
        });
        blocks.forEach(block -> {
            addFileId(fileIds, block.image());
            addFileId(fileIds, block.mobileImage());
        });
        blockItems.forEach(item -> addFileId(fileIds, item.image()));

        Map<String, DirectusCatalogueClient.DirectusFileAsset> fileAssetsById = loadFileAssets(fileIds);
        Map<Integer, List<ContentModels.PageSectionItem>> itemsByBlockId = mapBlockItems(blockItems, fileAssetsById);
        Map<String, List<ContentModels.PageSection>> blocksByOwnerKey = blocks.stream()
                .collect(Collectors.groupingBy(
                        block -> normalizeNullableKey(block.ownerKey()),
                        Collectors.mapping(
                                block -> toPageSection(block, itemsByBlockId.getOrDefault(block.id(), List.of()), fileAssetsById),
                                Collectors.collectingAndThen(Collectors.toList(), list -> list.stream()
                                        .filter(section -> section != null)
                                        .sorted(Comparator.comparing(ContentModels.PageSection::sort, Comparator.nullsLast(Integer::compareTo))
                                                .thenComparing(ContentModels.PageSection::internalName, Comparator.nullsLast(String::compareToIgnoreCase)))
                                        .toList())
                        )
                ));

        return overlays.stream()
                .filter(overlay -> StringUtils.hasText(overlay.overlayKey()))
                .collect(Collectors.toMap(
                        overlay -> normalizeRequiredKey(overlay.overlayKey(), "Overlay key is required"),
                        overlay -> new CatalogueContentModels.CatalogueOverlay(
                                overlay.id(),
                                normalizeRequiredKey(overlay.overlayKey(), "Overlay key is required"),
                                entityKind,
                                overlay.status(),
                                overlay.publishedAt(),
                                overlay.seoTitle(),
                                overlay.seoDescription(),
                                toMediaAsset(overlay.seoImage(), null, fileAssetsById),
                                overlay.badgeText(),
                                overlay.ribbonText(),
                                overlay.marketingTitle(),
                                overlay.marketingSubtitle(),
                                overlay.introBody(),
                                toHeroSection(
                                        entityKind,
                                        overlay.overlayKey(),
                                        overlay.heroEyebrow(),
                                        overlay.heroTitle(),
                                        overlay.heroAccent(),
                                        overlay.heroBody(),
                                        overlay.heroImage(),
                                        overlay.heroImageAlt(),
                                        overlay.heroMobileImage(),
                                        overlay.heroMobileImageAlt(),
                                        overlay.heroPrimaryCtaLabel(),
                                        overlay.heroPrimaryCtaUrl(),
                                        overlay.heroSecondaryCtaLabel(),
                                        overlay.heroSecondaryCtaUrl(),
                                        overlay.heroStyleVariant(),
                                        overlay.heroLayoutVariant(),
                                        overlay.publishedAt(),
                                        fileAssetsById
                                ),
                                blocksByOwnerKey.getOrDefault(normalizeRequiredKey(overlay.overlayKey(), "Overlay key is required"), List.of()),
                                parseStringList(overlay.linkedCollectionKeys())
                        ),
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
    }

    private CatalogueContentModels.StorefrontCollectionDefinition loadStorefrontCollectionDefinition(String key, ContentAccessMode accessMode) {
        DirectusCatalogueClient.DirectusStorefrontCollection collection = directusCatalogueClient.fetchStorefrontCollection(key, accessMode);
        List<DirectusCatalogueClient.DirectusStorefrontCollectionItem> rules = directusCatalogueClient.fetchStorefrontCollectionItems(collection.id(), accessMode);

        Set<String> fileIds = new LinkedHashSet<>();
        addFileId(fileIds, collection.seoImage());
        addFileId(fileIds, collection.heroImage());
        addFileId(fileIds, collection.heroMobileImage());
        Map<String, DirectusCatalogueClient.DirectusFileAsset> fileAssetsById = loadFileAssets(fileIds);

        return new CatalogueContentModels.StorefrontCollectionDefinition(
                collection.key(),
                collection.title(),
                collection.description(),
                collection.mode(),
                collection.ruleType(),
                normalizeNullableKey(collection.categoryKey()),
                normalizeNullableKey(collection.brandKey()),
                collection.limit(),
                collection.sortMode(),
                collection.seoTitle(),
                collection.seoDescription(),
                toMediaAsset(collection.seoImage(), null, fileAssetsById),
                toHeroSection(
                        "collection",
                        collection.key(),
                        collection.heroEyebrow(),
                        collection.heroTitle(),
                        collection.heroAccent(),
                        collection.heroBody(),
                        collection.heroImage(),
                        collection.heroImageAlt(),
                        collection.heroMobileImage(),
                        collection.heroMobileImageAlt(),
                        collection.heroPrimaryCtaLabel(),
                        collection.heroPrimaryCtaUrl(),
                        collection.heroSecondaryCtaLabel(),
                        collection.heroSecondaryCtaUrl(),
                        collection.heroStyleVariant(),
                        collection.heroLayoutVariant(),
                        collection.publishedAt(),
                        fileAssetsById
                ),
                collection.primaryCtaLabel(),
                collection.primaryCtaUrl(),
                collection.status(),
                collection.publishedAt(),
                rules.stream()
                        .map(rule -> new CatalogueContentModels.StorefrontCollectionRuleItem(
                                normalizeNullableKey(rule.entityKind()),
                                normalizeNullableKey(rule.entityKey()),
                                normalizeNullableKey(rule.behavior()),
                                rule.sort()
                        ))
                        .filter(rule -> StringUtils.hasText(rule.entityKind()) && StringUtils.hasText(rule.entityKey()))
                        .sorted(Comparator.comparing(CatalogueContentModels.StorefrontCollectionRuleItem::sort, Comparator.nullsLast(Integer::compareTo))
                                .thenComparing(CatalogueContentModels.StorefrontCollectionRuleItem::entityKey, Comparator.nullsLast(String::compareToIgnoreCase)))
                        .toList()
        );
    }

    private Map<Integer, List<ContentModels.PageSectionItem>> mapBlockItems(
            List<DirectusCatalogueClient.DirectusOverlayBlockItem> blockItems,
            Map<String, DirectusCatalogueClient.DirectusFileAsset> fileAssetsById
    ) {
        return blockItems.stream()
                .collect(Collectors.groupingBy(
                        DirectusCatalogueClient.DirectusOverlayBlockItem::overlayBlock,
                        Collectors.mapping(
                                item -> new ContentModels.PageSectionItem(
                                        item.title(),
                                        item.description(),
                                        item.label(),
                                        item.url(),
                                        toMediaAsset(item.image(), item.imageAlt(), fileAssetsById),
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
    }

    private ContentModels.PageSection toPageSection(
            DirectusCatalogueClient.DirectusOverlayBlock block,
            List<ContentModels.PageSectionItem> items,
            Map<String, DirectusCatalogueClient.DirectusFileAsset> fileAssetsById
    ) {
        if (block == null) {
            return null;
        }

        List<ContentModels.PageSectionItem> resolvedItems = items;
        if (resolvedItems.isEmpty() && StringUtils.hasText(block.collectionKey())) {
            resolvedItems = List.of(new ContentModels.PageSectionItem(
                    null,
                    null,
                    null,
                    null,
                    null,
                    "collection",
                    normalizeNullableKey(block.collectionKey()),
                    block.sort(),
                    block.publishedAt()
            ));
        }

        return new ContentModels.PageSection(
                block.internalName(),
                block.blockType(),
                block.sort(),
                block.anchorId(),
                block.eyebrow(),
                block.title(),
                block.accent(),
                block.body(),
                toMediaAsset(block.image(), block.imageAlt(), fileAssetsById),
                toMediaAsset(block.mobileImage(), block.mobileImageAlt(), fileAssetsById),
                block.primaryCtaLabel(),
                block.primaryCtaUrl(),
                block.secondaryCtaLabel(),
                block.secondaryCtaUrl(),
                block.styleVariant(),
                block.layoutVariant(),
                block.publishedAt(),
                resolvedItems
        );
    }

    private ContentModels.PageSection toHeroSection(
            String ownerKind,
            String ownerKey,
            String eyebrow,
            String title,
            String accent,
            String body,
            String image,
            String imageAlt,
            String mobileImage,
            String mobileImageAlt,
            String primaryCtaLabel,
            String primaryCtaUrl,
            String secondaryCtaLabel,
            String secondaryCtaUrl,
            String styleVariant,
            String layoutVariant,
            java.time.OffsetDateTime publishedAt,
            Map<String, DirectusCatalogueClient.DirectusFileAsset> fileAssetsById
    ) {
        if (!StringUtils.hasText(eyebrow)
                && !StringUtils.hasText(title)
                && !StringUtils.hasText(accent)
                && !StringUtils.hasText(body)
                && !StringUtils.hasText(image)
                && !StringUtils.hasText(mobileImage)
                && !StringUtils.hasText(primaryCtaLabel)
                && !StringUtils.hasText(primaryCtaUrl)
                && !StringUtils.hasText(secondaryCtaLabel)
                && !StringUtils.hasText(secondaryCtaUrl)) {
            return null;
        }

        return new ContentModels.PageSection(
                ownerKind + "-hero:" + normalizeNullableKey(ownerKey),
                "hero",
                0,
                "hero",
                eyebrow,
                title,
                accent,
                body,
                toMediaAsset(image, imageAlt, fileAssetsById),
                toMediaAsset(mobileImage, mobileImageAlt, fileAssetsById),
                primaryCtaLabel,
                primaryCtaUrl,
                secondaryCtaLabel,
                secondaryCtaUrl,
                styleVariant,
                layoutVariant,
                publishedAt,
                List.of()
        );
    }

    private Map<String, DirectusCatalogueClient.DirectusFileAsset> loadFileAssets(Iterable<String> fileIds) {
        if (fileIds == null) {
            return Map.of();
        }

        Set<String> normalizedIds = new LinkedHashSet<>();
        fileIds.forEach(fileId -> addFileId(normalizedIds, fileId));
        if (normalizedIds.isEmpty()) {
            return Map.of();
        }

        try {
            return directusCatalogueClient.fetchFiles(normalizedIds).stream()
                    .filter(asset -> StringUtils.hasText(asset.id()))
                    .collect(Collectors.toMap(
                            DirectusCatalogueClient.DirectusFileAsset::id,
                            Function.identity(),
                            (left, right) -> left
                    ));
        } catch (RuntimeException error) {
            log.warn("Failed to fetch Directus catalogue file metadata for {} asset(s). Returning URL-only payloads.", normalizedIds.size(), error);
            return Map.of();
        }
    }

    private ContentModels.MediaAsset toMediaAsset(
            String fileId,
            String altOverride,
            Map<String, DirectusCatalogueClient.DirectusFileAsset> fileAssetsById
    ) {
        if (!StringUtils.hasText(fileId)) {
            return null;
        }

        String normalizedId = fileId.trim();
        DirectusCatalogueClient.DirectusFileAsset asset = fileAssetsById.get(normalizedId);
        String alt = StringUtils.hasText(altOverride)
                ? altOverride.trim()
                : asset != null
                ? firstText(asset.title(), asset.description())
                : "";

        return new ContentModels.MediaAsset(
                normalizedId,
                directusCatalogueClient.assetUrl(normalizedId),
                asset != null ? asset.width() : null,
                asset != null ? asset.height() : null,
                alt,
                asset != null ? asset.type() : null
        );
    }

    private String firstText(String first, String second) {
        if (StringUtils.hasText(first)) {
            return first.trim();
        }

        if (StringUtils.hasText(second)) {
            return second.trim();
        }

        return "";
    }

    private List<String> parseStringList(Object rawValue) {
        if (rawValue == null) {
            return List.of();
        }

        if (rawValue instanceof Collection<?> list) {
            return list.stream()
                    .map(String::valueOf)
                    .map(this::normalizeNullableKey)
                    .filter(StringUtils::hasText)
                    .distinct()
                    .toList();
        }

        String rawText = String.valueOf(rawValue).trim();
        if (!StringUtils.hasText(rawText)) {
            return List.of();
        }

        String normalized = rawText;
        if (normalized.startsWith("[") && normalized.endsWith("]")) {
            normalized = normalized.substring(1, normalized.length() - 1);
        }

        return List.of(normalized.split(",")).stream()
                .map(token -> token.replace("\"", "").replace("'", ""))
                .map(this::normalizeNullableKey)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
    }

    private List<String> normalizeKeys(Collection<String> keys) {
        if (keys == null) {
            return List.of();
        }

        return keys.stream()
                .map(this::normalizeNullableKey)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
    }

    private String normalizeRequiredKey(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(message);
        }

        return normalizeNullableKey(value);
    }

    private String normalizeNullableKey(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }

        return value.trim().toLowerCase(Locale.ROOT);
    }

    private void addFileId(Set<String> fileIds, String rawFileId) {
        if (!StringUtils.hasText(rawFileId)) {
            return;
        }

        fileIds.add(rawFileId.trim());
    }
}
