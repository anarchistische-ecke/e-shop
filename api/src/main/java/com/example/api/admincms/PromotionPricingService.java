package com.example.api.admincms;

import com.example.cart.domain.Cart;
import com.example.cart.service.CartPricingService;
import com.example.catalog.domain.Category;
import com.example.catalog.domain.Product;
import com.example.catalog.domain.ProductVariant;
import com.example.common.domain.Money;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.util.Locale;

@Service
public class PromotionPricingService implements CartPricingService {

    private final PromotionRepository promotionRepository;
    private final PromoCodeRepository promoCodeRepository;

    public PromotionPricingService(PromotionRepository promotionRepository, PromoCodeRepository promoCodeRepository) {
        this.promotionRepository = promotionRepository;
        this.promoCodeRepository = promoCodeRepository;
    }

    @Override
    public Money resolveUnitPrice(ProductVariant variant) {
        if (variant == null || variant.getPrice() == null) {
            return null;
        }
        Product product = variant.getProduct();
        long base = variant.getPrice().getAmount();
        long best = base;
        for (Promotion promotion : promotionRepository.findByStatusIgnoreCase("ACTIVE")) {
            if (!isActiveNow(promotion) || !targetsVariant(promotion, product, variant)) {
                continue;
            }
            long candidate = applyPromotion(base, promotion);
            if (candidate >= 0 && candidate < best) {
                best = candidate;
            }
        }
        return Money.of(best, variant.getPrice().getCurrency());
    }

    @Override
    public long calculateCartTotal(Cart cart) {
        long subtotal = CartPricingService.super.calculateCartTotal(cart);
        long best = subtotal;
        for (Promotion promotion : promotionRepository.findByStatusIgnoreCase("ACTIVE")) {
            if (!isActiveNow(promotion) || !"CART_THRESHOLD".equalsIgnoreCase(promotion.getType())) {
                continue;
            }
            Long threshold = promotion.getThresholdAmount();
            if (threshold != null && subtotal < threshold) {
                continue;
            }
            long candidate = applyPromotion(subtotal, promotion);
            if (candidate >= 0 && candidate < best) {
                best = candidate;
            }
        }
        if (StringUtils.hasText(cart.getPromoCode())) {
            long promoCodeCandidate = promoCodeRepository.findByCodeIgnoreCase(cart.getPromoCode())
                    .filter(this::isActiveNow)
                    .filter(promoCode -> promoCode.getThresholdAmount() == null || subtotal >= promoCode.getThresholdAmount())
                    .filter(promoCode -> promoCode.getMaxRedemptions() == null || promoCode.getRedemptionCount() < promoCode.getMaxRedemptions())
                    .map(promoCode -> applyPromoCode(subtotal, promoCode))
                    .orElse(best);
            if (promoCodeCandidate >= 0 && promoCodeCandidate < best) {
                best = promoCodeCandidate;
            }
        }
        return best;
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
}
