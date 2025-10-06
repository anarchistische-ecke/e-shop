package ru.postel_yug.eshop.cart.entity;

import lombok.Data;

import java.math.BigDecimal;

public class CartItem {
    private Long id;
    private Long productId;
    private int quantity;

    public CartItem() {}

    public CartItem(Long productId, int quantity) {
        this.productId = productId;
        this.quantity = quantity;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }
}

