package ru.postel_yug.eshop.cart.dto;

import ru.postel_yug.eshop.catalog.dto.ProductInfo;

public class CartItemDto {
    private ProductInfo product;
    private int quantity;

    public CartItemDto() {
    }

    public CartItemDto(ProductInfo product, int quantity) {
        this.product = product;
        this.quantity = quantity;
    }

    public ProductInfo getProduct() {
        return product;
    }

    public void setProduct(ProductInfo product) {
        this.product = product;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }
}


