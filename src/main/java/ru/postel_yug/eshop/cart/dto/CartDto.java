package ru.postel_yug.eshop.cart.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

public class CartDto {
    private Long id;
    private Long userId;
    private List<CartItemDto> items;
    private double totalPrice;

    public CartDto() {}

    public CartDto(Long id, Long userId,
                   List<CartItemDto> items, double totalPrice) {
        this.id = id;
        this.userId = userId;
        this.items = items;
        this.totalPrice = totalPrice;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public List<CartItemDto> getItems() {
        return items;
    }

    public void setItems(List<CartItemDto> items) {
        this.items = items;
    }

    public double getTotalPrice() {
        return totalPrice;
    }

    public void setTotalPrice(double totalPrice) {
        this.totalPrice = totalPrice;
    }
}

