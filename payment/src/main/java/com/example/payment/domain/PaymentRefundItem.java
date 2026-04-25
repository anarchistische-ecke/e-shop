package com.example.payment.domain;

import com.example.common.domain.BaseEntity;
import com.example.common.domain.Money;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

@Entity
@Table(name = "payment_refund_item")
public class PaymentRefundItem extends BaseEntity {
    @NotNull
    @Column(name = "payment_id", nullable = false, columnDefinition = "uuid")
    private UUID paymentId;

    @NotBlank
    @Column(name = "refund_id", nullable = false)
    private String refundId;

    @NotNull
    @Column(name = "order_item_id", nullable = false, columnDefinition = "uuid")
    private UUID orderItemId;

    @Min(1)
    @Column(name = "quantity", nullable = false)
    private int quantity;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "amount", column = @Column(name = "refund_amount", nullable = false)),
            @AttributeOverride(name = "currency", column = @Column(name = "refund_currency", nullable = false, length = 3))
    })
    @NotNull
    private Money refundAmount;

    @Column(name = "refund_status")
    private String refundStatus;

    public UUID getPaymentId() {
        return paymentId;
    }

    public void setPaymentId(UUID paymentId) {
        this.paymentId = paymentId;
    }

    public String getRefundId() {
        return refundId;
    }

    public void setRefundId(String refundId) {
        this.refundId = refundId;
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

    public Money getRefundAmount() {
        return refundAmount;
    }

    public void setRefundAmount(Money refundAmount) {
        this.refundAmount = refundAmount;
    }

    public String getRefundStatus() {
        return refundStatus;
    }

    public void setRefundStatus(String refundStatus) {
        this.refundStatus = refundStatus;
    }
}
