package com.example.payment.domain;

import com.example.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

@Entity
@Table(name = "saved_payment_method")
public class SavedPaymentMethod extends BaseEntity {
    @NotNull
    @Column(name = "customer_id", nullable = false, columnDefinition = "uuid")
    private UUID customerId;

    @NotBlank
    @Column(name = "provider_payment_method_id", nullable = false, unique = true)
    private String providerPaymentMethodId;

    @Column(name = "method_type")
    private String methodType;

    @Column(name = "method_status")
    private String methodStatus;

    @Column(name = "title")
    private String title;

    @Column(name = "card_last4")
    private String cardLast4;

    @Column(name = "card_first6")
    private String cardFirst6;

    @Column(name = "card_type")
    private String cardType;

    @Column(name = "card_expiry_month")
    private String cardExpiryMonth;

    @Column(name = "card_expiry_year")
    private String cardExpiryYear;

    @Column(name = "card_issuer")
    private String cardIssuer;

    public UUID getCustomerId() {
        return customerId;
    }

    public void setCustomerId(UUID customerId) {
        this.customerId = customerId;
    }

    public String getProviderPaymentMethodId() {
        return providerPaymentMethodId;
    }

    public void setProviderPaymentMethodId(String providerPaymentMethodId) {
        this.providerPaymentMethodId = providerPaymentMethodId;
    }

    public String getMethodType() {
        return methodType;
    }

    public void setMethodType(String methodType) {
        this.methodType = methodType;
    }

    public String getMethodStatus() {
        return methodStatus;
    }

    public void setMethodStatus(String methodStatus) {
        this.methodStatus = methodStatus;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getCardLast4() {
        return cardLast4;
    }

    public void setCardLast4(String cardLast4) {
        this.cardLast4 = cardLast4;
    }

    public String getCardFirst6() {
        return cardFirst6;
    }

    public void setCardFirst6(String cardFirst6) {
        this.cardFirst6 = cardFirst6;
    }

    public String getCardType() {
        return cardType;
    }

    public void setCardType(String cardType) {
        this.cardType = cardType;
    }

    public String getCardExpiryMonth() {
        return cardExpiryMonth;
    }

    public void setCardExpiryMonth(String cardExpiryMonth) {
        this.cardExpiryMonth = cardExpiryMonth;
    }

    public String getCardExpiryYear() {
        return cardExpiryYear;
    }

    public void setCardExpiryYear(String cardExpiryYear) {
        this.cardExpiryYear = cardExpiryYear;
    }

    public String getCardIssuer() {
        return cardIssuer;
    }

    public void setCardIssuer(String cardIssuer) {
        this.cardIssuer = cardIssuer;
    }
}
