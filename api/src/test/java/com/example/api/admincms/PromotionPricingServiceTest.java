package com.example.api.admincms;

import com.example.cart.domain.Cart;
import com.example.cart.domain.CartItem;
import com.example.cart.service.CartPricingSummary;
import com.example.catalog.domain.Product;
import com.example.catalog.domain.ProductVariant;
import com.example.catalog.repository.ProductVariantRepository;
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
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PromotionPricingServiceTest {

    @Mock
    private PromotionRepository promotionRepository;

    @Mock
    private PromoCodeRepository promoCodeRepository;

    @Mock
    private ProductVariantRepository variantRepository;

    private PromotionPricingService service;

    @BeforeEach
    void setUp() {
        service = new PromotionPricingService(promotionRepository, promoCodeRepository, variantRepository);
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
    void calculateCartTotal_usesBetterEligiblePromoCodeThanFixedThresholdTier() {
        Cart cart = new Cart(UUID.randomUUID());
        cart.setPromoCode("SAVE20");
        CartItem item = new CartItem(UUID.randomUUID(), 1, Money.of(6_000_000, "RUB"));
        cart.addItem(item);

        PromoCode promoCode = new PromoCode();
        promoCode.setCode("SAVE20");
        promoCode.setStatus("ACTIVE");
        promoCode.setDiscountPercent(20);
        promoCode.setThresholdAmount(5_000_000L);

        when(promoCodeRepository.findByCodeIgnoreCase("SAVE20")).thenReturn(Optional.of(promoCode));

        assertThat(service.calculateCartTotal(cart)).isEqualTo(4_800_000L);
    }

    @Test
    void calculateCartPricing_countsSaleItemsTowardThresholdButDiscountsOnlyNonSaleItems() {
        UUID saleVariantId = UUID.randomUUID();
        UUID regularVariantId = UUID.randomUUID();
        Product saleProduct = new Product("Sale Sheet", null, "sale-sheet");
        Product regularProduct = new Product("Regular Sheet", null, "regular-sheet");
        ProductVariant saleVariant = new ProductVariant("SALE-SKU", "Sale", Money.of(4_000_000, "RUB"), 5);
        saleVariant.setId(saleVariantId);
        saleVariant.setProduct(saleProduct);
        ProductVariant regularVariant = new ProductVariant("REGULAR-SKU", "Regular", Money.of(2_500_000, "RUB"), 5);
        regularVariant.setId(regularVariantId);
        regularVariant.setProduct(regularProduct);

        Promotion sale = activePromotion("PRODUCT_SALE");
        sale.setSalePriceAmount(3_000_000L);
        PromotionTarget target = new PromotionTarget();
        target.setTargetKind("VARIANT");
        target.setTargetKey("SALE-SKU");
        sale.addTarget(target);

        Cart cart = new Cart(UUID.randomUUID());
        cart.addItem(new CartItem(saleVariantId, 1, Money.of(4_000_000, "RUB")));
        cart.addItem(new CartItem(regularVariantId, 1, Money.of(2_500_000, "RUB")));

        when(variantRepository.findByIdIn(anyCollection())).thenReturn(List.of(saleVariant, regularVariant));
        when(promotionRepository.findByStatusIgnoreCase("ACTIVE")).thenReturn(List.of(sale));

        CartPricingSummary pricing = service.calculateCartPricing(cart);

        assertThat(pricing.saleSubtotal().getAmount()).isEqualTo(5_500_000L);
        assertThat(pricing.eligibleDiscountSubtotal().getAmount()).isEqualTo(2_500_000L);
        assertThat(pricing.productSaleDiscount().getAmount()).isEqualTo(1_000_000L);
        assertThat(pricing.thresholdDiscount().getAmount()).isEqualTo(125_000L);
        assertThat(pricing.finalTotal().getAmount()).isEqualTo(5_375_000L);
    }

    @Test
    void calculateCartPricing_usesThresholdWhenItBeatsValidPromoCode() {
        Cart cart = new Cart(UUID.randomUUID());
        cart.setPromoCode("SMALL");
        cart.addItem(new CartItem(UUID.randomUUID(), 1, Money.of(6_000_000, "RUB")));

        PromoCode promoCode = new PromoCode();
        promoCode.setCode("SMALL");
        promoCode.setStatus("ACTIVE");
        promoCode.setDiscountPercent(3);

        when(promoCodeRepository.findByCodeIgnoreCase("SMALL")).thenReturn(Optional.of(promoCode));

        CartPricingSummary pricing = service.calculateCartPricing(cart);

        assertThat(pricing.promoCodeStatus()).isEqualTo("VALID");
        assertThat(pricing.promoCodeDiscount().getAmount()).isEqualTo(180_000L);
        assertThat(pricing.cartDiscount().getAmount()).isEqualTo(300_000L);
        assertThat(pricing.appliedCartDiscountType()).isEqualTo("CART_THRESHOLD");
        assertThat(pricing.finalTotal().getAmount()).isEqualTo(5_700_000L);
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
