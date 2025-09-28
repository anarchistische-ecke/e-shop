package ru.postel_yug.eshop.cart.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

@Data
public class CartItemDto {
    private String productId;
    private String productName;
    private int quantity;
    private BigDecimal pricePerUnit;
    private BigDecimal totalPrice;

    public CartItemDto(String productId, String productName, int quantity, BigDecimal pricePerUnit, BigDecimal totalPrice) {
    }
}
