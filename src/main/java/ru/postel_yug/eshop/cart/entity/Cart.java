package ru.postel_yug.eshop.cart.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class Cart {
    private Long id;
    private Long userId;
    private List<CartItem> items = new ArrayList<>();

    public Cart() {}

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

    public List<CartItem> getItems() {
        return items;
    }

    public void setItems(List<CartItem> items) {
        this.items = items;
    }
}


