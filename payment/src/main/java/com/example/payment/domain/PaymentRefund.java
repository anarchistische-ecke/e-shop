package com.example.payment.domain;

import com.example.common.domain.BaseEntity;
import com.example.common.domain.Money;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "payment_refund")
public class PaymentRefund extends BaseEntity {
    @NotNull
    @Column(name = "payment_id", nullable = false, columnDefinition = "uuid")
    private UUID paymentId;

    @NotBlank
    @Column(name = "refund_id", nullable = false, unique = true)
    private String refundId;

    @Column(name = "refund_status")
    private String refundStatus;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "amount", column = @Column(name = "refund_amount")),
            @AttributeOverride(name = "currency", column = @Column(name = "refund_currency", length = 3))
    })
    private Money refundAmount;

    @Column(name = "refund_date", columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private OffsetDateTime refundDate;

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

    public String getRefundStatus() {
        return refundStatus;
    }

    public void setRefundStatus(String refundStatus) {
        this.refundStatus = refundStatus;
    }

    public Money getRefundAmount() {
        return refundAmount;
    }

    public void setRefundAmount(Money refundAmount) {
        this.refundAmount = refundAmount;
    }

    public OffsetDateTime getRefundDate() {
        return refundDate;
    }

    public void setRefundDate(OffsetDateTime refundDate) {
        this.refundDate = refundDate;
    }
}
