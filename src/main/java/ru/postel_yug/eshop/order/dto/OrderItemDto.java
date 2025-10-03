package ru.postel_yug.eshop.order.dto;

import java.math.BigDecimal;

public class OrderItemDto {
    private String productName;
    private int quantity;
    private BigDecimal price;
    private BigDecimal total;
    // static from(OrderItem i) ...
}
