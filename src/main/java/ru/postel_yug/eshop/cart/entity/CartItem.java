package ru.postel_yug.eshop.cart.entity;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class CartItem {
    private String productId;
    private String productName;
    private int quantity;
    private BigDecimal pricePerUnit;

    public CartItem() {

    }

    public CartItem(String productId, String productName, int quantity, BigDecimal pricePerUnit) {

    }

}
