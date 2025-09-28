package ru.postel_yug.eshop.cart.dto;

import lombok.Data;

@Data
public class CartItemRequestDto {
    private String productId;
    private int quantity;
}
