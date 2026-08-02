package com.example.api.catalog;

import com.example.catalog.domain.Category;
import com.example.catalog.domain.Product;
import com.example.catalog.domain.ProductImage;
import com.example.catalog.domain.ProductVariant;
import com.example.catalog.service.CatalogService;
import com.example.common.domain.Money;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.text.Normalizer;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/catalogue/listing")
public class CatalogueListingController {

    private static final int DEFAULT_SIZE = 12;
    private static final int MAX_SIZE = 24;

    private final CatalogService catalogService;
    private final CatalogueResponseFactory responseFactory;

    public CatalogueListingController(
            CatalogService catalogService,
            CatalogueResponseFactory responseFactory
    ) {
        this.catalogService = catalogService;
        this.responseFactory = responseFactory;
    }

    @GetMapping
    public ResponseEntity<ListingResponse> getListing(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String scope,
            @RequestParam(required = false, name = "q") String query,
            @RequestParam(required = false) String brand,
            @RequestParam(required = false) Long minPriceMinor,
            @RequestParam(required = false) Long maxPriceMinor,
            @RequestParam(defaultValue = "false") boolean inStock,
            @RequestParam(defaultValue = "false") boolean sale,
            @RequestParam(defaultValue = "bestMatch") String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + DEFAULT_SIZE) int size
    ) {
        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, Math.min(size, MAX_SIZE));
        String effectiveCategory = firstText(category, scope);
        String normalizedQuery = normalize(query);

        List<Product> categoryProducts = catalogService.getProducts(effectiveCategory, null).stream()
                .filter(Product::isIsActive)
                .toList();
        Map<UUID, ProductImage> primaryImages = catalogService.getPrimaryProductImages(
                categoryProducts.stream().map(Product::getId).toList()
        );
        List<Candidate> categoryCandidates = categoryProducts.stream()
                .map(product -> toCandidate(product, primaryImages.get(product.getId())))
                .toList();

        SearchResult searchResult = applySearch(categoryCandidates, normalizedQuery);
        List<Candidate> facetCandidates = searchResult.candidates();
        Facets facets = buildFacets(facetCandidates, effectiveCategory);

        long lowerBound = minPriceMinor != null ? Math.max(0L, minPriceMinor) : Long.MIN_VALUE;
        long upperBound = maxPriceMinor != null ? Math.max(0L, maxPriceMinor) : Long.MAX_VALUE;
        if (lowerBound > upperBound) {
            long swap = lowerBound;
            lowerBound = upperBound;
            upperBound = swap;
        }
        final long minBound = lowerBound;
        final long maxBound = upperBound;

        List<Candidate> filtered = facetCandidates.stream()
                .filter(candidate -> !StringUtils.hasText(brand)
                        || Objects.equals(normalize(candidate.brandSlug()), normalize(brand)))
                .filter(candidate -> candidate.priceMinor() >= minBound && candidate.priceMinor() <= maxBound)
                .filter(candidate -> !inStock || candidate.stock() > 0)
                .filter(candidate -> !sale || candidate.onSale())
                .sorted(candidateComparator(sort, normalizedQuery))
                .toList();

        int totalItems = filtered.size();
        int totalPages = totalItems == 0 ? 0 : (int) Math.ceil(totalItems / (double) safeSize);
        int fromIndex = Math.min(totalItems, safePage * safeSize);
        int toIndex = Math.min(totalItems, fromIndex + safeSize);
        List<ProductCard> items = filtered.subList(fromIndex, toIndex).stream()
                .map(this::toProductCard)
                .toList();

        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofSeconds(60)).cachePublic())
                .body(new ListingResponse(
                        items,
                        safePage,
                        safeSize,
                        totalItems,
                        totalPages,
                        searchResult.appliedQuery(),
                        searchResult.correction(),
                        facets
                ));
    }

    private Candidate toCandidate(Product product, ProductImage primaryImage) {
        ProductVariant primaryVariant = primaryVariant(product);
        CatalogController.VariantResponse variant = primaryVariant != null
                ? responseFactory.toVariantResponse(primaryVariant)
                : null;
        long priceMinor = variant != null && variant.getPrice() != null
                ? variant.getPrice().getAmount()
                : Long.MAX_VALUE;
        int stock = product.getVariants() == null
                ? 0
                : product.getVariants().stream()
                        .filter(Objects::nonNull)
                        .mapToInt(ProductVariant::getStockQuantity)
                        .sum();

        return new Candidate(
                product,
                primaryVariant,
                variant,
                primaryImage,
                priceMinor,
                stock,
                variant != null && variant.isOnSale(),
                product.getBrand() != null ? product.getBrand().getSlug() : null,
                product.getBrand() != null ? product.getBrand().getName() : null,
                searchText(product),
                0
        );
    }

    private ProductVariant primaryVariant(Product product) {
        if (product == null || product.getVariants() == null || product.getVariants().isEmpty()) {
            return null;
        }
        Comparator<ProductVariant> comparator = Comparator
                .comparing((ProductVariant variant) -> variant.getStockQuantity() <= 0)
                .thenComparing(variant -> Optional.ofNullable(variant.getSortOrder()).orElse(Integer.MAX_VALUE))
                .thenComparing(variant -> variant.getPrice() != null
                        ? variant.getPrice().getAmount()
                        : Long.MAX_VALUE)
                .thenComparing(variant -> Optional.ofNullable(variant.getName()).orElse(""));
        return product.getVariants().stream()
                .filter(Objects::nonNull)
                .min(comparator)
                .orElse(null);
    }

    private SearchResult applySearch(List<Candidate> candidates, String normalizedQuery) {
        if (!StringUtils.hasText(normalizedQuery)) {
            return new SearchResult(candidates, "", null);
        }

        List<Candidate> direct = rankedMatches(candidates, normalizedQuery);
        if (!direct.isEmpty()) {
            return new SearchResult(direct, normalizedQuery, null);
        }

        String correction = resolveCorrection(candidates, normalizedQuery);
        if (!StringUtils.hasText(correction)) {
            return new SearchResult(List.of(), normalizedQuery, null);
        }
        List<Candidate> corrected = rankedMatches(candidates, correction);
        return new SearchResult(
                corrected,
                corrected.isEmpty() ? normalizedQuery : correction,
                corrected.isEmpty() ? null : correction
        );
    }

    private List<Candidate> rankedMatches(List<Candidate> candidates, String query) {
        return candidates.stream()
                .map(candidate -> candidate.withSearchScore(score(candidate.searchText(), query)))
                .filter(candidate -> candidate.searchScore() > 0)
                .sorted(Comparator.comparingInt(Candidate::searchScore).reversed())
                .toList();
    }

    private int score(String searchable, String query) {
        if (!StringUtils.hasText(query) || !StringUtils.hasText(searchable)) {
            return 0;
        }
        if (searchable.equals(query)) {
            return 1000;
        }
        if (searchable.startsWith(query)) {
            return 800;
        }
        if (searchable.contains(query)) {
            return 600;
        }

        String[] queryTokens = query.split(" ");
        String[] valueTokens = searchable.split(" ");
        int score = 0;
        for (String queryToken : queryTokens) {
            int best = 0;
            for (String valueToken : valueTokens) {
                if (valueToken.startsWith(queryToken)) {
                    best = Math.max(best, 120);
                } else if (levenshtein(valueToken, queryToken) <= Math.max(1, queryToken.length() / 4)) {
                    best = Math.max(best, 80);
                }
            }
            if (best == 0) {
                return 0;
            }
            score += best;
        }
        return score;
    }

    private String resolveCorrection(List<Candidate> candidates, String query) {
        if (!StringUtils.hasText(query) || query.contains(" ")) {
            return null;
        }
        Set<String> dictionary = new LinkedHashSet<>();
        candidates.forEach(candidate -> {
            for (String token : candidate.searchText().split(" ")) {
                if (token.length() >= 3) {
                    dictionary.add(token);
                }
            }
        });
        return dictionary.stream()
                .map(value -> Map.entry(value, levenshtein(value, query)))
                .filter(entry -> entry.getValue() <= Math.max(1, query.length() / 3))
                .min(Comparator
                        .comparingInt((Map.Entry<String, Integer> entry) -> entry.getValue())
                        .thenComparing(Map.Entry::getKey))
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    private Comparator<Candidate> candidateComparator(String sort, String query) {
        Comparator<Candidate> chosen = switch (Optional.ofNullable(sort).orElse("bestMatch")) {
            case "newest" -> Comparator.comparing(
                    candidate -> Optional.ofNullable(candidate.product().getCreatedAt()).orElse(OffsetDateTime.MIN),
                    Comparator.reverseOrder()
            );
            case "priceAsc" -> Comparator.comparingLong(Candidate::priceMinor);
            case "priceDesc" -> Comparator.comparingLong(Candidate::priceMinor).reversed();
            case "discount" -> Comparator.comparingInt(
                    (Candidate candidate) -> Optional.ofNullable(candidate.variantResponse())
                            .map(CatalogController.VariantResponse::getDiscountPercent)
                            .orElse(0)
            ).reversed();
            default -> StringUtils.hasText(query)
                    ? Comparator.comparingInt(Candidate::searchScore).reversed()
                    : Comparator.comparingInt(Candidate::stock).reversed();
        };
        return Comparator.comparing((Candidate candidate) -> candidate.primaryImage() == null)
                .thenComparing(chosen)
                .thenComparing(candidate -> Optional.ofNullable(candidate.product().getName()).orElse(""), String.CASE_INSENSITIVE_ORDER);
    }

    private Facets buildFacets(List<Candidate> candidates, String effectiveCategory) {
        long min = candidates.stream().mapToLong(Candidate::priceMinor)
                .filter(value -> value != Long.MAX_VALUE)
                .min()
                .orElse(0L);
        long max = candidates.stream().mapToLong(Candidate::priceMinor)
                .filter(value -> value != Long.MAX_VALUE)
                .max()
                .orElse(0L);

        Map<String, BrandAccumulator> brands = new LinkedHashMap<>();
        candidates.forEach(candidate -> {
            if (!StringUtils.hasText(candidate.brandSlug())) {
                return;
            }
            brands.computeIfAbsent(
                    candidate.brandSlug(),
                    ignored -> new BrandAccumulator(candidate.brandSlug(), candidate.brandName())
            ).increment();
        });

        Optional<Category> activeCategory = StringUtils.hasText(effectiveCategory)
                ? catalogService.getBySlug(effectiveCategory)
                : Optional.empty();
        List<CategoryFacet> childCategories = catalogService.listAllInCategory().stream()
                .filter(Category::isIsActive)
                .filter(category -> activeCategory
                        .map(parent -> category.getParent() != null
                                && Objects.equals(category.getParent().getId(), parent.getId()))
                        .orElseGet(() -> category.getParent() == null))
                .map(category -> new CategoryFacet(
                        category.getId(),
                        category.getSlug(),
                        category.getName(),
                        countCategoryCandidates(candidates, category)
                ))
                .filter(facet -> facet.count() > 0)
                .toList();

        return new Facets(
                new PriceFacet(min, max),
                brands.values().stream()
                        .map(value -> new BrandFacet(value.slug, value.name, value.count))
                        .sorted(Comparator.comparing(BrandFacet::name, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                        .toList(),
                childCategories
        );
    }

    private long countCategoryCandidates(List<Candidate> candidates, Category category) {
        String path = Optional.ofNullable(category.getFullPath()).orElse(category.getSlug());
        return candidates.stream()
                .filter(candidate -> candidate.product().getCategories() != null
                        && candidate.product().getCategories().stream()
                                .filter(Objects::nonNull)
                                .anyMatch(productCategory -> Optional.ofNullable(productCategory.getFullPath())
                                        .orElse(productCategory.getSlug())
                                        .startsWith(path)))
                .count();
    }

    private ProductCard toProductCard(Candidate candidate) {
        Product product = candidate.product();
        CatalogController.VariantResponse variant = candidate.variantResponse();
        List<CatalogController.CategorySummary> categories = categorySummaries(product);
        List<CatalogController.ImageResponse> images = candidate.primaryImage() != null
                ? List.of(responseFactory.toImageResponse(candidate.primaryImage()))
                : List.of();
        MediaModels.MediaManifest media = candidate.primaryImage() != null
                ? responseFactory.toMediaManifest(candidate.primaryImage(), product.getName())
                : null;

        return new ProductCard(
                product.getId(),
                product.getSlug(),
                product.getName(),
                summarize(product.getDescription()),
                summarize(product.getDescription()),
                categories.isEmpty() ? null : categories.getFirst().getSlug(),
                categories,
                candidate.brandSlug(),
                candidate.brandName(),
                variant,
                variant != null ? List.of(variant) : List.of(),
                variant != null ? variant.getPrice() : null,
                variant != null ? variant.getOldPrice() : null,
                candidate.onSale(),
                variant != null ? variant.getDiscountPercent() : null,
                candidate.stock(),
                candidate.stock() > 0,
                images,
                media,
                materialSizeAttributes(product, candidate.primaryVariant()),
                badges(candidate)
        );
    }

    private List<CatalogController.CategorySummary> categorySummaries(Product product) {
        if (product.getCategories() == null) {
            return List.of();
        }
        return product.getCategories().stream()
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(category -> Optional.ofNullable(category.getFullPath()).orElse(category.getSlug())))
                .map(category -> new CatalogController.CategorySummary(
                        category.getId(),
                        category.getName(),
                        category.getSlug(),
                        category.getFullPath()
                ))
                .toList();
    }

    private List<String> materialSizeAttributes(Product product, ProductVariant variant) {
        LinkedHashSet<String> attributes = new LinkedHashSet<>();
        responseFactory.deserializeSpecifications(product.getSpecifications()).stream()
                .flatMap(section -> Optional.ofNullable(section.getItems()).orElse(List.of()).stream())
                .filter(item -> StringUtils.hasText(item.getValue()))
                .filter(item -> {
                    String label = normalize(item.getLabel());
                    return label.contains("материал")
                            || label.contains("ткан")
                            || label.contains("состав")
                            || label.contains("размер");
                })
                .limit(2)
                .forEach(item -> attributes.add(item.getLabel() + ": " + item.getValue()));
        if (variant != null && StringUtils.hasText(variant.getSizeLabel())) {
            attributes.add("Размер: " + variant.getSizeLabel());
        }
        if (variant != null && StringUtils.hasText(variant.getColorLabel())) {
            attributes.add("Цвет: " + variant.getColorLabel());
        }
        return attributes.stream().limit(3).toList();
    }

    private List<String> badges(Candidate candidate) {
        List<String> badges = new ArrayList<>();
        if (candidate.onSale()) {
            badges.add("sale");
        }
        if (candidate.stock() <= 0) {
            badges.add("out_of_stock");
        } else if (candidate.stock() <= 3) {
            badges.add("low_stock");
        } else {
            badges.add("in_stock");
        }
        return badges;
    }

    private String searchText(Product product) {
        StringBuilder value = new StringBuilder();
        append(value, product.getName());
        append(value, product.getDescription());
        append(value, product.getSlug());
        if (product.getBrand() != null) {
            append(value, product.getBrand().getName());
            append(value, product.getBrand().getSlug());
        }
        if (product.getCategories() != null) {
            product.getCategories().forEach(category -> {
                if (category != null) {
                    append(value, category.getName());
                    append(value, category.getSlug());
                }
            });
        }
        return normalize(value.toString());
    }

    private void append(StringBuilder builder, String value) {
        if (StringUtils.hasText(value)) {
            builder.append(' ').append(value);
        }
    }

    private String summarize(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String compact = value.replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ").trim();
        return compact.length() <= 160 ? compact : compact.substring(0, 157).trim() + "...";
    }

    private String firstText(String first, String second) {
        if (StringUtils.hasText(first)) {
            return first.trim();
        }
        return StringUtils.hasText(second) ? second.trim() : null;
    }

    private String normalize(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return Normalizer.normalize(value, Normalizer.Form.NFKD)
                .toLowerCase(Locale.ROOT)
                .replace('ё', 'е')
                .replaceAll("\\p{M}+", "")
                .replaceAll("[^\\p{L}\\p{N}]+", " ")
                .trim()
                .replaceAll("\\s+", " ");
    }

    private int levenshtein(String left, String right) {
        int[] previous = new int[right.length() + 1];
        int[] current = new int[right.length() + 1];
        for (int index = 0; index <= right.length(); index++) {
            previous[index] = index;
        }
        for (int leftIndex = 1; leftIndex <= left.length(); leftIndex++) {
            current[0] = leftIndex;
            for (int rightIndex = 1; rightIndex <= right.length(); rightIndex++) {
                int substitution = previous[rightIndex - 1]
                        + (left.charAt(leftIndex - 1) == right.charAt(rightIndex - 1) ? 0 : 1);
                current[rightIndex] = Math.min(
                        Math.min(current[rightIndex - 1] + 1, previous[rightIndex] + 1),
                        substitution
                );
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return previous[right.length()];
    }

    private record Candidate(
            Product product,
            ProductVariant primaryVariant,
            CatalogController.VariantResponse variantResponse,
            ProductImage primaryImage,
            long priceMinor,
            int stock,
            boolean onSale,
            String brandSlug,
            String brandName,
            String searchText,
            int searchScore
    ) {
        Candidate withSearchScore(int score) {
            return new Candidate(
                    product,
                    primaryVariant,
                    variantResponse,
                    primaryImage,
                    priceMinor,
                    stock,
                    onSale,
                    brandSlug,
                    brandName,
                    searchText,
                    score
            );
        }
    }

    private record SearchResult(
            List<Candidate> candidates,
            String appliedQuery,
            String correction
    ) {
    }

    private static final class BrandAccumulator {
        private final String slug;
        private final String name;
        private int count;

        private BrandAccumulator(String slug, String name) {
            this.slug = slug;
            this.name = name;
        }

        private void increment() {
            count++;
        }
    }

    public record ListingResponse(
            List<ProductCard> items,
            int page,
            int size,
            int totalItems,
            int totalPages,
            String appliedQuery,
            String correction,
            Facets facets
    ) {
    }

    public record ProductCard(
            UUID id,
            String slug,
            String name,
            String summary,
            String description,
            String category,
            List<CatalogController.CategorySummary> categories,
            String brand,
            String brandName,
            CatalogController.VariantResponse primaryVariant,
            List<CatalogController.VariantResponse> variants,
            Money price,
            Money oldPrice,
            boolean onSale,
            Integer discountPercent,
            int stock,
            boolean inStock,
            List<CatalogController.ImageResponse> images,
            MediaModels.MediaManifest primaryMedia,
            List<String> attributes,
            List<String> badges
    ) {
    }

    public record Facets(
            PriceFacet price,
            List<BrandFacet> brands,
            List<CategoryFacet> childCategories
    ) {
    }

    public record PriceFacet(long minMinor, long maxMinor) {
    }

    public record BrandFacet(String slug, String name, int count) {
    }

    public record CategoryFacet(UUID id, String slug, String name, long count) {
    }
}
