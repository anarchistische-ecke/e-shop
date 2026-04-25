package com.example.cart.service;

import com.example.common.domain.Money;

import java.util.List;
import java.util.UUID;

public record CartPricingSummary(
        Money originalSubtotal,
        Money saleSubtotal,
        Money eligibleDiscountSubtotal,
        Money productSaleDiscount,
        Money thresholdDiscount,
        Money promoCodeDiscount,
        Money cartDiscount,
        Money totalDiscount,
        Money finalTotal,
        String appliedCartDiscountType,
        String appliedCartDiscountLabel,
        String promoCode,
        String promoCodeStatus,
        boolean promoCodeApplied,
        List<CartPricingLine> items
) {
    public static CartPricingSummary empty(String currency) {
        Money zero = Money.of(0L, currency == null || currency.isBlank() ? "RUB" : currency);
        return new CartPricingSummary(
                zero,
                zero,
                zero,
                zero,
                zero,
                zero,
                zero,
                zero,
                zero,
                null,
                null,
                null,
                null,
                false,
                List.of()
        );
    }

    public record CartPricingLine(
            UUID variantId,
            int quantity,
            Money originalUnitPrice,
            Money unitPrice,
            Money originalSubtotal,
            Money saleSubtotal,
            Money productSaleDiscount,
            Money cartDiscount,
            Money payableTotal,
            boolean saleApplied,
            UUID salePromotionId,
            String salePromotionName,
            String salePromotionType
    ) {
    }
}
