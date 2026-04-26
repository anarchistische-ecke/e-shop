package com.example.cart.service;

import com.example.common.domain.Money;

import java.util.UUID;

public record VariantPricing(
        UUID variantId,
        Money originalUnitPrice,
        Money unitPrice,
        boolean saleApplied,
        UUID salePromotionId,
        String salePromotionName,
        String salePromotionType
) {
    public long unitDiscountAmount() {
        if (originalUnitPrice == null || unitPrice == null) {
            return 0L;
        }
        return Math.max(0L, originalUnitPrice.getAmount() - unitPrice.getAmount());
    }
}
