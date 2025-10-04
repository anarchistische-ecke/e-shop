package ru.postel_yug.eshop.order.dto;

import java.io.Serializable;
import java.util.Objects;

public class PaymentDetails implements Serializable {

    private static final long serialVersionUID = 1L;

    private PaymentType type;
    private String cardToken;

    public PaymentDetails() {
    }

    public PaymentDetails(PaymentType type, String cardToken) {
        this.type = type;
        this.cardToken = cardToken;
    }

    public PaymentType getType() {
        return type;
    }

    public void setType(PaymentType type) {
        this.type = type;
    }

    public String getCardToken() {
        return cardToken;
    }

    public void setCardToken(String cardToken) {
        this.cardToken = cardToken;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PaymentDetails)) return false;
        PaymentDetails that = (PaymentDetails) o;
        return type == that.type && Objects.equals(cardToken, that.cardToken);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, cardToken);
    }

    @Override
    public String toString() {
        return "PaymentDetails{" +
                "type=" + type +
                ", cardToken='" + cardToken + '\'' +
                '}';
    }
}

