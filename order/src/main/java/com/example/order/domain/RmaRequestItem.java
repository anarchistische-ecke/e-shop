package com.example.order.domain;

import com.example.common.domain.BaseEntity;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

@Entity
@Table(
        name = "rma_request_item",
        uniqueConstraints = @UniqueConstraint(name = "uk_rma_request_item_order_item", columnNames = {"rma_request_id", "order_item_id"})
)
public class RmaRequestItem extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rma_request_id", nullable = false, columnDefinition = "uuid")
    @JsonIgnore
    private RmaRequest rmaRequest;

    @NotNull
    @Column(name = "order_item_id", nullable = false, columnDefinition = "uuid")
    private UUID orderItemId;

    @Min(1)
    @Column(name = "quantity", nullable = false)
    private int quantity;

    public RmaRequestItem() {
    }

    public RmaRequestItem(UUID orderItemId, int quantity) {
        this.orderItemId = orderItemId;
        this.quantity = quantity;
    }

    public RmaRequest getRmaRequest() {
        return rmaRequest;
    }

    public void setRmaRequest(RmaRequest rmaRequest) {
        this.rmaRequest = rmaRequest;
    }

    public UUID getOrderItemId() {
        return orderItemId;
    }

    public void setOrderItemId(UUID orderItemId) {
        this.orderItemId = orderItemId;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }
}
