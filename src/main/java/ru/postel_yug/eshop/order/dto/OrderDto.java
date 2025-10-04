package ru.postel_yug.eshop.order.dto;

import ru.postel_yug.eshop.order.entity.Order;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class OrderDto {
    private Long id;
    private String status;
    private BigDecimal totalPrice;
    private LocalDateTime createdAt;

    public OrderDto(Long id, LocalDateTime createdAt, BigDecimal totalPrice) {
    }

    public OrderDto() {
    }

    public static OrderDto from(Order order) {
        if (order == null) {
            return null;
        }
        return new OrderDto(order.getId(), order.getCreatedAt(), order.getTotalPrice());
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public BigDecimal getTotalPrice() {
        return totalPrice;
    }

    public void setTotalPrice(BigDecimal totalPrice) {
        this.totalPrice = totalPrice;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
