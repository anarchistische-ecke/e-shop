package com.example.shipment.domain;

import com.example.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "shipment")
public class Shipment extends BaseEntity {
    @NotNull
    @Column(name = "order_id", nullable = false, columnDefinition = "uuid")
    private UUID orderId;

    @NotBlank
    @Column(name = "carrier", nullable = false)
    private String carrier;

    @NotBlank
    @Column(name = "tracking_number", nullable = false)
    private String trackingNumber;

    @Column(name = "shipped_at", columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private OffsetDateTime shippedAt;

    @Column(name = "delivered_at", columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private OffsetDateTime deliveredAt;

    public Shipment() {
    }

    public Shipment(UUID orderId, String carrier, String trackingNumber) {
        this.orderId = orderId;
        this.carrier = carrier;
        this.trackingNumber = trackingNumber;
        this.shippedAt = OffsetDateTime.now();
    }

    public UUID getOrderId() {
        return orderId;
    }

    public void setOrderId(UUID orderId) {
        this.orderId = orderId;
    }

    public String getCarrier() {
        return carrier;
    }

    public void setCarrier(String carrier) {
        this.carrier = carrier;
    }

    public String getTrackingNumber() {
        return trackingNumber;
    }

    public void setTrackingNumber(String trackingNumber) {
        this.trackingNumber = trackingNumber;
    }

    public OffsetDateTime getShippedAt() {
        return shippedAt;
    }

    public void setShippedAt(OffsetDateTime shippedAt) {
        this.shippedAt = shippedAt;
    }

    public OffsetDateTime getDeliveredAt() {
        return deliveredAt;
    }

    public void setDeliveredAt(OffsetDateTime deliveredAt) {
        this.deliveredAt = deliveredAt;
    }
}