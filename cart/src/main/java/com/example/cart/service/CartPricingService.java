package com.example.cart.service;

import com.example.cart.domain.Cart;
import com.example.cart.domain.CartItem;
import com.example.catalog.domain.ProductVariant;
import com.example.common.domain.Money;

import java.util.List;
import java.util.Objects;

public interface CartPricingService {

    default VariantPricing resolveVariantPricing(ProductVariant variant) {
        Money price = variant != null ? variant.getPrice() : null;
        return new VariantPricing(
                variant != null ? variant.getId() : null,
                price,
                price,
                false,
                null,
                null,
                null
        );
    }

    default Money resolveUnitPrice(ProductVariant variant) {
        return resolveVariantPricing(variant).unitPrice();
    }

    default long calculateCartTotal(Cart cart) {
        return calculateCartPricing(cart).finalTotal().getAmount();
    }

    default CartPricingSummary calculateCartPricing(Cart cart) {
        if (cart == null || cart.getItems() == null) {
            return CartPricingSummary.empty("RUB");
        }
        String currency = cart.getItems().stream()
                .map(CartItem::getUnitPrice)
                .filter(Objects::nonNull)
                .map(Money::getCurrency)
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .orElse("RUB");
        List<CartPricingSummary.CartPricingLine> lines = cart.getItems().stream()
                .map(item -> {
                    Money unitPrice = item.getUnitPrice();
                    Money originalUnitPrice = unitPrice != null ? unitPrice : Money.of(0L, currency);
                    long lineTotal = originalUnitPrice.getAmount() * item.getQuantity();
                    Money lineMoney = Money.of(lineTotal, originalUnitPrice.getCurrency());
                    return new CartPricingSummary.CartPricingLine(
                            item.getVariantId(),
                            item.getQuantity(),
                            originalUnitPrice,
                            unitPrice,
                            lineMoney,
                            lineMoney,
                            Money.of(0L, originalUnitPrice.getCurrency()),
                            Money.of(0L, originalUnitPrice.getCurrency()),
                            lineMoney,
                            false,
                            null,
                            null,
                            null
                    );
                })
                .toList();
        long subtotal = lines.stream().mapToLong(line -> line.saleSubtotal().getAmount()).sum();
        Money subtotalMoney = Money.of(subtotal, currency);
        return new CartPricingSummary(
                subtotalMoney,
                subtotalMoney,
                subtotalMoney,
                Money.of(0L, currency),
                Money.of(0L, currency),
                Money.of(0L, currency),
                Money.of(0L, currency),
                Money.of(0L, currency),
                subtotalMoney,
                null,
                null,
                cart.getPromoCode(),
                null,
                false,
                lines
        );
    }
}
