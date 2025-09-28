package ru.postel_yug.eshop.cart.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class CartDto {
    private String cartId;
    private List<CartItemDto> items;
    private String couponCode;
    private BigDecimal totalPrice;
    private BigDecimal totalDiscount;
    private BigDecimal totalPriceWithDiscount;
    private int totalItems;
    private int totalQuantity;
}
