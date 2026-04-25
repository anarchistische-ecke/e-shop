package com.example.payment.domain;

import com.example.common.domain.BaseEntity;
import com.example.common.domain.Money;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "payment")
public class Payment extends BaseEntity {
    @NotNull
    @Column(name = "order_id", nullable = false, columnDefinition = "uuid")
    private UUID orderId;

    @Embedded
    @NotNull
    private Money amount;

    @NotBlank
    @Column(name = "method", nullable = false)
    private String method;

    @NotNull
    @Column(name = "status", nullable = false)
    @Enumerated(EnumType.STRING)
    private PaymentStatus status;

    @Column(name = "payment_date", nullable = false, columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private OffsetDateTime paymentDate;

    @Column(name = "provider_payment_id")
    private String providerPaymentId;

    @Column(name = "confirmation_url")
    private String confirmationUrl;

    @Column(name = "confirmation_type")
    private String confirmationType;

    @Column(name = "confirmation_token")
    private String confirmationToken;

    @Column(name = "receipt_registration")
    private String receiptRegistration;

    @Column(name = "receipt_url")
    private String receiptUrl;

    @Column(name = "refund_id")
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

    public Payment() {
    }

    public Payment(UUID orderId, Money amount, String method, PaymentStatus status) {
        this.orderId = orderId;
        this.amount = amount;
        this.method = method;
        this.status = status;
        this.paymentDate = OffsetDateTime.now();
    }

    public UUID getOrderId() {
        return orderId;
    }

    public void setOrderId(UUID orderId) {
        this.orderId = orderId;
    }

    public Money getAmount() {
        return amount;
    }

    public void setAmount(Money amount) {
        this.amount = amount;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public void setStatus(PaymentStatus status) {
        this.status = status;
    }

    public OffsetDateTime getPaymentDate() {
        return paymentDate;
    }

    public void setPaymentDate(OffsetDateTime paymentDate) {
        this.paymentDate = paymentDate;
    }

    public String getProviderPaymentId() {
        return providerPaymentId;
    }

    public void setProviderPaymentId(String providerPaymentId) {
        this.providerPaymentId = providerPaymentId;
    }

    public String getConfirmationUrl() {
        return confirmationUrl;
    }

    public void setConfirmationUrl(String confirmationUrl) {
        this.confirmationUrl = confirmationUrl;
    }

    public String getConfirmationType() {
        return confirmationType;
    }

    public void setConfirmationType(String confirmationType) {
        this.confirmationType = confirmationType;
    }

    public String getConfirmationToken() {
        return confirmationToken;
    }

    public void setConfirmationToken(String confirmationToken) {
        this.confirmationToken = confirmationToken;
    }

    public String getReceiptRegistration() {
        return receiptRegistration;
    }

    public void setReceiptRegistration(String receiptRegistration) {
        this.receiptRegistration = receiptRegistration;
    }

    public String getReceiptUrl() {
        return receiptUrl;
    }

    public void setReceiptUrl(String receiptUrl) {
        this.receiptUrl = receiptUrl;
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
