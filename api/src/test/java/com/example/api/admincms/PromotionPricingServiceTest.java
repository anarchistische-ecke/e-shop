package com.example.api.admincms;

import com.example.cart.domain.Cart;
import com.example.cart.domain.CartItem;
import com.example.catalog.domain.Product;
import com.example.catalog.domain.ProductVariant;
import com.example.common.domain.Money;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PromotionPricingServiceTest {

    @Mock
    private PromotionRepository promotionRepository;

    @Mock
    private PromoCodeRepository promoCodeRepository;

    private PromotionPricingService service;

    @BeforeEach
    void setUp() {
        service = new PromotionPricingService(promotionRepository, promoCodeRepository);
    }

    @Test
    void resolveUnitPrice_appliesActiveTargetedProductSale() {
        Product product = new Product("Sheet", null, "sheet");
        ProductVariant variant = new ProductVariant("SKU-1", "White", Money.of(10_000, "RUB"), 4);
        product.addVariant(variant);

        Promotion promotion = activePromotion("PRODUCT_SALE");
        promotion.setSalePriceAmount(7_000L);
        PromotionTarget target = new PromotionTarget();
        target.setTargetKind("VARIANT");
        target.setTargetKey("SKU-1");
        promotion.addTarget(target);

        when(promotionRepository.findByStatusIgnoreCase("ACTIVE")).thenReturn(List.of(promotion));

        assertThat(service.resolveUnitPrice(variant).getAmount()).isEqualTo(7_000L);
    }

    @Test
    void calculateCartTotal_usesBetterEligiblePromoCodeThanThresholdPromotion() {
        Cart cart = new Cart(UUID.randomUUID());
        cart.setPromoCode("SAVE20");
        CartItem item = new CartItem(UUID.randomUUID(), 1, Money.of(10_000, "RUB"));
        cart.addItem(item);

        Promotion thresholdPromotion = activePromotion("CART_THRESHOLD");
        thresholdPromotion.setThresholdAmount(5_000L);
        thresholdPromotion.setDiscountAmount(1_000L);

        PromoCode promoCode = new PromoCode();
        promoCode.setCode("SAVE20");
        promoCode.setStatus("ACTIVE");
        promoCode.setDiscountPercent(20);
        promoCode.setThresholdAmount(5_000L);

        when(promotionRepository.findByStatusIgnoreCase("ACTIVE")).thenReturn(List.of(thresholdPromotion));
        when(promoCodeRepository.findByCodeIgnoreCase("SAVE20")).thenReturn(Optional.of(promoCode));

        assertThat(service.calculateCartTotal(cart)).isEqualTo(8_000L);
    }

    private Promotion activePromotion(String type) {
        Promotion promotion = new Promotion();
        promotion.setName(type);
        promotion.setType(type);
        promotion.setStatus("ACTIVE");
        promotion.setStartsAt(OffsetDateTime.now().minusDays(1));
        promotion.setEndsAt(OffsetDateTime.now().plusDays(1));
        return promotion;
    }
}
