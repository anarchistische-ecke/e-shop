package ru.postel_yug.eshop.cart.entity;

import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
public class Cart {

    private List<CartItem> items = new ArrayList<>();
    private String couponCode;

    private BigDecimal totalPrice = BigDecimal.ZERO;
    private BigDecimal totalDiscount = BigDecimal.ZERO;
    private BigDecimal totalPriceWithDiscount = BigDecimal.ZERO;
    private int totalItems = 0;
    private int totalQuantity = 0;

    public Cart() {

    }
}
