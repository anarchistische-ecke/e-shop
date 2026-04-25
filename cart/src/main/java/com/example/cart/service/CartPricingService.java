package com.example.cart.service;

import com.example.cart.domain.Cart;
import com.example.catalog.domain.ProductVariant;
import com.example.common.domain.Money;

public interface CartPricingService {

    default Money resolveUnitPrice(ProductVariant variant) {
        return variant != null ? variant.getPrice() : null;
    }

    default long calculateCartTotal(Cart cart) {
        if (cart == null || cart.getItems() == null) {
            return 0L;
        }
        return cart.getItems().stream().mapToLong(item -> item.getTotalAmount()).sum();
    }
}
