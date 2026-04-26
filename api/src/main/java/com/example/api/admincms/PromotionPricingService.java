package com.example.api.admincms;

import com.example.cart.domain.Cart;
import com.example.cart.domain.CartItem;
import com.example.cart.service.CartPricingSummary;
import com.example.cart.service.CartPricingService;
import com.example.cart.service.VariantPricing;
import com.example.catalog.domain.Category;
import com.example.catalog.domain.Product;
import com.example.catalog.domain.ProductVariant;
import com.example.catalog.repository.ProductVariantRepository;
import com.example.common.domain.Money;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
public class PromotionPricingService implements CartPricingService {

    private static final List<ThresholdTier> THRESHOLD_TIERS = List.of(
            new ThresholdTier(10_000_000L, 10, "Скидка 10% от 100 000 ₽"),
            new ThresholdTier(6_500_000L, 7, "Скидка 7% от 65 000 ₽"),
            new ThresholdTier(5_000_000L, 5, "Скидка 5% от 50 000 ₽")
    );

    private final PromotionRepository promotionRepository;
    private final PromoCodeRepository promoCodeRepository;
    private final ProductVariantRepository variantRepository;

    public PromotionPricingService(PromotionRepository promotionRepository,
                                   PromoCodeRepository promoCodeRepository,
                                   ProductVariantRepository variantRepository) {
        this.promotionRepository = promotionRepository;
        this.promoCodeRepository = promoCodeRepository;
        this.variantRepository = variantRepository;
    }

    @Override
    public VariantPricing resolveVariantPricing(ProductVariant variant) {
        if (variant == null || variant.getPrice() == null) {
            return new VariantPricing(null, null, null, false, null, null, null);
        }
        Product product = variant.getProduct();
        long base = variant.getPrice().getAmount();
        Promotion bestPromotion = null;
        long bestAmount = base;
        for (Promotion promotion : activePromotions()) {
            if (!"PRODUCT_SALE".equalsIgnoreCase(promotion.getType())
                    || !sameCurrency(variant.getPrice(), promotion)
                    || !targetsVariant(promotion, product, variant)) {
                continue;
            }
            long candidate = applyPromotion(base, promotion);
            if (candidate >= 0 && candidate < bestAmount) {
                bestAmount = candidate;
                bestPromotion = promotion;
            }
        }
        boolean saleApplied = bestPromotion != null && bestAmount < base;
        return new VariantPricing(
                variant.getId(),
                variant.getPrice(),
                Money.of(bestAmount, variant.getPrice().getCurrency()),
                saleApplied,
                saleApplied ? bestPromotion.getId() : null,
                saleApplied ? bestPromotion.getName() : null,
                saleApplied ? bestPromotion.getType() : null
        );
    }

    @Override
    public long calculateCartTotal(Cart cart) {
        return calculateCartPricing(cart).finalTotal().getAmount();
    }

    @Override
    public CartPricingSummary calculateCartPricing(Cart cart) {
        if (cart == null || cart.getItems() == null || cart.getItems().isEmpty()) {
            return CartPricingSummary.empty("RUB");
        }

        Map<UUID, ProductVariant> variantsById = variantsById(cart.getItems().stream()
                .map(CartItem::getVariantId)
                .filter(Objects::nonNull)
                .toList());
        String currency = resolveCartCurrency(cart, variantsById);
        List<CartPricingSummary.CartPricingLine> initialLines = new ArrayList<>();
        long originalSubtotal = 0L;
        long saleSubtotal = 0L;
        long eligibleSubtotal = 0L;
        long productSaleDiscount = 0L;

        for (CartItem item : cart.getItems()) {
            ProductVariant variant = variantsById.get(item.getVariantId());
            VariantPricing pricing = variant != null ? resolveVariantPricing(variant) : fallbackPricing(item, currency);
            Money originalUnitPrice = pricing.originalUnitPrice() != null ? pricing.originalUnitPrice() : Money.of(0L, currency);
            Money unitPrice = pricing.unitPrice() != null ? pricing.unitPrice() : originalUnitPrice;
            long lineOriginal = originalUnitPrice.getAmount() * item.getQuantity();
            long lineSale = unitPrice.getAmount() * item.getQuantity();
            long lineSaleDiscount = Math.max(0L, lineOriginal - lineSale);
            originalSubtotal += lineOriginal;
            saleSubtotal += lineSale;
            productSaleDiscount += lineSaleDiscount;
            if (!pricing.saleApplied()) {
                eligibleSubtotal += lineSale;
            }
            initialLines.add(new CartPricingSummary.CartPricingLine(
                    item.getVariantId(),
                    item.getQuantity(),
                    originalUnitPrice,
                    unitPrice,
                    Money.of(lineOriginal, originalUnitPrice.getCurrency()),
                    Money.of(lineSale, unitPrice.getCurrency()),
                    Money.of(lineSaleDiscount, unitPrice.getCurrency()),
                    Money.of(0L, unitPrice.getCurrency()),
                    Money.of(lineSale, unitPrice.getCurrency()),
                    pricing.saleApplied(),
                    pricing.salePromotionId(),
                    pricing.salePromotionName(),
                    pricing.salePromotionType()
            ));
        }

        ThresholdDiscount thresholdDiscount = thresholdDiscount(saleSubtotal, eligibleSubtotal);
        PromoCodeDiscount promoCodeDiscount = promoCodeDiscount(cart.getPromoCode(), saleSubtotal, eligibleSubtotal);
        boolean usePromoCode = promoCodeDiscount.valid() && promoCodeDiscount.amount() > 0
                && promoCodeDiscount.amount() >= thresholdDiscount.amount();
        boolean useThreshold = !usePromoCode && thresholdDiscount.amount() > 0;
        long cartDiscountAmount = usePromoCode ? promoCodeDiscount.amount() : useThreshold ? thresholdDiscount.amount() : 0L;
        String appliedType = usePromoCode ? "PROMO_CODE" : useThreshold ? "CART_THRESHOLD" : null;
        String appliedLabel = usePromoCode ? promoCodeDiscount.label() : useThreshold ? thresholdDiscount.label() : null;
        List<CartPricingSummary.CartPricingLine> finalLines = allocateCartDiscount(initialLines, cartDiscountAmount, eligibleSubtotal, currency);
        long finalTotal = Math.max(0L, saleSubtotal - cartDiscountAmount);
        long totalDiscount = productSaleDiscount + cartDiscountAmount;

        return new CartPricingSummary(
                Money.of(originalSubtotal, currency),
                Money.of(saleSubtotal, currency),
                Money.of(eligibleSubtotal, currency),
                Money.of(productSaleDiscount, currency),
                Money.of(thresholdDiscount.amount(), currency),
                Money.of(promoCodeDiscount.amount(), currency),
                Money.of(cartDiscountAmount, currency),
                Money.of(totalDiscount, currency),
                Money.of(finalTotal, currency),
                appliedType,
                appliedLabel,
                cart.getPromoCode(),
                promoCodeDiscount.status(),
                usePromoCode,
                finalLines
        );
    }

    private long applyPromotion(long baseAmount, Promotion promotion) {
        if ("PRODUCT_SALE".equalsIgnoreCase(promotion.getType()) && promotion.getSalePriceAmount() != null) {
            return promotion.getSalePriceAmount();
        }
        if (promotion.getDiscountPercent() != null && promotion.getDiscountPercent() > 0) {
            return Math.max(0L, Math.round(baseAmount * (100 - promotion.getDiscountPercent()) / 100.0d));
        }
        if (promotion.getDiscountAmount() != null && promotion.getDiscountAmount() > 0) {
            return Math.max(0L, baseAmount - promotion.getDiscountAmount());
        }
        return baseAmount;
    }

    private long applyPromoCode(long baseAmount, PromoCode promoCode) {
        if (promoCode.getDiscountPercent() != null && promoCode.getDiscountPercent() > 0) {
            return Math.max(0L, Math.round(baseAmount * (100 - promoCode.getDiscountPercent()) / 100.0d));
        }
        if (promoCode.getDiscountAmount() != null && promoCode.getDiscountAmount() > 0) {
            return Math.max(0L, baseAmount - promoCode.getDiscountAmount());
        }
        return baseAmount;
    }

    private Map<UUID, ProductVariant> variantsById(Collection<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        Map<UUID, ProductVariant> result = new HashMap<>();
        variantRepository.findByIdIn(ids).forEach(variant -> result.put(variant.getId(), variant));
        return result;
    }

    private List<Promotion> activePromotions() {
        return promotionRepository.findByStatusIgnoreCase("ACTIVE").stream()
                .filter(this::isActiveNow)
                .toList();
    }

    private String resolveCartCurrency(Cart cart, Map<UUID, ProductVariant> variantsById) {
        return cart.getItems().stream()
                .map(item -> Optional.ofNullable(variantsById.get(item.getVariantId()))
                        .map(ProductVariant::getPrice)
                        .orElse(item.getUnitPrice()))
                .filter(Objects::nonNull)
                .map(Money::getCurrency)
                .filter(StringUtils::hasText)
                .findFirst()
                .orElse("RUB");
    }

    private VariantPricing fallbackPricing(CartItem item, String currency) {
        Money price = item != null && item.getUnitPrice() != null ? item.getUnitPrice() : Money.of(0L, currency);
        return new VariantPricing(
                item != null ? item.getVariantId() : null,
                price,
                price,
                false,
                null,
                null,
                null
        );
    }

    private ThresholdDiscount thresholdDiscount(long saleSubtotal, long eligibleSubtotal) {
        if (eligibleSubtotal <= 0L) {
            return new ThresholdDiscount(0L, null);
        }
        return THRESHOLD_TIERS.stream()
                .filter(tier -> saleSubtotal >= tier.thresholdAmount())
                .findFirst()
                .map(tier -> new ThresholdDiscount(percentDiscount(eligibleSubtotal, tier.percent()), tier.label()))
                .orElseGet(() -> new ThresholdDiscount(0L, null));
    }

    private PromoCodeDiscount promoCodeDiscount(String code, long saleSubtotal, long eligibleSubtotal) {
        if (!StringUtils.hasText(code)) {
            return new PromoCodeDiscount(0L, null, "NOT_PROVIDED", false);
        }
        String normalizedCode = code.trim().toUpperCase(Locale.ROOT);
        Optional<PromoCode> match = promoCodeRepository.findByCodeIgnoreCase(normalizedCode);
        if (match.isEmpty()) {
            return new PromoCodeDiscount(0L, normalizedCode, "NOT_FOUND", false);
        }
        PromoCode promoCode = match.get();
        if (!isActiveNow(promoCode)) {
            return new PromoCodeDiscount(0L, normalizedCode, "INACTIVE", false);
        }
        if (promoCode.getMaxRedemptions() != null && promoCode.getRedemptionCount() >= promoCode.getMaxRedemptions()) {
            return new PromoCodeDiscount(0L, normalizedCode, "LIMIT_REACHED", false);
        }
        if (promoCode.getThresholdAmount() != null && saleSubtotal < promoCode.getThresholdAmount()) {
            return new PromoCodeDiscount(0L, normalizedCode, "BELOW_THRESHOLD", false);
        }
        long discountedEligible = applyPromoCode(eligibleSubtotal, promoCode);
        long discount = Math.max(0L, eligibleSubtotal - discountedEligible);
        return new PromoCodeDiscount(discount, "Промокод " + normalizedCode, "VALID", true);
    }

    private long percentDiscount(long amount, int percent) {
        if (amount <= 0L || percent <= 0) {
            return 0L;
        }
        return Math.max(0L, Math.round(amount * percent / 100.0d));
    }

    private List<CartPricingSummary.CartPricingLine> allocateCartDiscount(
            List<CartPricingSummary.CartPricingLine> lines,
            long cartDiscountAmount,
            long eligibleSubtotal,
            String currency
    ) {
        if (lines == null || lines.isEmpty() || cartDiscountAmount <= 0L || eligibleSubtotal <= 0L) {
            return lines != null ? lines : List.of();
        }
        List<CartPricingSummary.CartPricingLine> eligibleLines = lines.stream()
                .filter(line -> !line.saleApplied() && line.saleSubtotal().getAmount() > 0)
                .toList();
        long allocated = 0L;
        List<CartPricingSummary.CartPricingLine> result = new ArrayList<>();
        for (CartPricingSummary.CartPricingLine line : lines) {
            long lineCartDiscount = 0L;
            if (!line.saleApplied() && line.saleSubtotal().getAmount() > 0) {
                boolean isLastEligible = eligibleLines.indexOf(line) == eligibleLines.size() - 1;
                lineCartDiscount = isLastEligible
                        ? Math.max(0L, cartDiscountAmount - allocated)
                        : Math.round((double) cartDiscountAmount * line.saleSubtotal().getAmount() / eligibleSubtotal);
                lineCartDiscount = Math.min(lineCartDiscount, line.saleSubtotal().getAmount());
                allocated += lineCartDiscount;
            }
            long payable = Math.max(0L, line.saleSubtotal().getAmount() - lineCartDiscount);
            result.add(new CartPricingSummary.CartPricingLine(
                    line.variantId(),
                    line.quantity(),
                    line.originalUnitPrice(),
                    line.unitPrice(),
                    line.originalSubtotal(),
                    line.saleSubtotal(),
                    line.productSaleDiscount(),
                    Money.of(lineCartDiscount, currency),
                    Money.of(payable, currency),
                    line.saleApplied(),
                    line.salePromotionId(),
                    line.salePromotionName(),
                    line.salePromotionType()
            ));
        }
        return result;
    }

    private boolean sameCurrency(Money basePrice, Promotion promotion) {
        if (basePrice == null || !StringUtils.hasText(basePrice.getCurrency())) {
            return true;
        }
        if (promotion == null || !StringUtils.hasText(promotion.getCurrency())) {
            return true;
        }
        return basePrice.getCurrency().equalsIgnoreCase(promotion.getCurrency());
    }

    private boolean targetsVariant(Promotion promotion, Product product, ProductVariant variant) {
        if (promotion.getTargets() == null || promotion.getTargets().isEmpty()) {
            return true;
        }
        return promotion.getTargets().stream().anyMatch(target -> {
            String kind = normalize(target.getTargetKind());
            String key = normalize(target.getTargetKey());
            if (!StringUtils.hasText(kind) || !StringUtils.hasText(key)) {
                return false;
            }
            if ("variant".equals(kind)) {
                return key.equals(normalize(variant.getSku())) || key.equals(normalize(String.valueOf(variant.getId())));
            }
            if (product == null) {
                return false;
            }
            if ("product".equals(kind)) {
                return key.equals(normalize(product.getSlug())) || key.equals(normalize(String.valueOf(product.getId())));
            }
            if ("brand".equals(kind) && product.getBrand() != null) {
                return key.equals(normalize(product.getBrand().getSlug())) || key.equals(normalize(String.valueOf(product.getBrand().getId())));
            }
            if ("category".equals(kind) && product.getCategories() != null) {
                return product.getCategories().stream().anyMatch(category -> categoryMatches(category, key));
            }
            return false;
        });
    }

    private boolean categoryMatches(Category category, String key) {
        if (category == null) {
            return false;
        }
        return key.equals(normalize(category.getSlug()))
                || key.equals(normalize(category.getFullPath()))
                || key.equals(normalize(String.valueOf(category.getId())));
    }

    private boolean isActiveNow(Promotion promotion) {
        OffsetDateTime now = OffsetDateTime.now();
        return promotion != null
                && "ACTIVE".equalsIgnoreCase(promotion.getStatus())
                && (promotion.getStartsAt() == null || !promotion.getStartsAt().isAfter(now))
                && (promotion.getEndsAt() == null || !promotion.getEndsAt().isBefore(now));
    }

    private boolean isActiveNow(PromoCode promoCode) {
        OffsetDateTime now = OffsetDateTime.now();
        return promoCode != null
                && "ACTIVE".equalsIgnoreCase(promoCode.getStatus())
                && (promoCode.getStartsAt() == null || !promoCode.getStartsAt().isAfter(now))
                && (promoCode.getEndsAt() == null || !promoCode.getEndsAt().isBefore(now));
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private record ThresholdTier(long thresholdAmount, int percent, String label) {
    }

    private record ThresholdDiscount(long amount, String label) {
    }

    private record PromoCodeDiscount(long amount, String label, String status, boolean valid) {
    }
}
