package com.example.order.domain;

import com.example.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "order_checkout_attempt")
public class OrderCheckoutAttempt extends BaseEntity {

    @Column(name = "key_value", nullable = false, unique = true)
    private String keyValue;

    @Column(name = "request_hash", nullable = false)
    private String requestHash;

    @Column(name = "order_id", columnDefinition = "uuid")
    private UUID orderId;

    public OrderCheckoutAttempt() {
    }

    public OrderCheckoutAttempt(String keyValue, String requestHash) {
        this.keyValue = keyValue;
        this.requestHash = requestHash;
    }

    public String getKeyValue() {
        return keyValue;
    }

    public void setKeyValue(String keyValue) {
        this.keyValue = keyValue;
    }

    public String getRequestHash() {
        return requestHash;
    }

    public void setRequestHash(String requestHash) {
        this.requestHash = requestHash;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public void setOrderId(UUID orderId) {
        this.orderId = orderId;
    }
}
