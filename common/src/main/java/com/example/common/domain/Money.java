package com.example.common.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Embeddable
public class Money {

    @Column(name = "amount", nullable = false)
    private long amount;

    @NotBlank
    @Column(name = "currency", length = 3, nullable = false)
    private String currency;

    protected Money() {

    }

    public Money(long amount, String currency) {
        this.amount = amount;
        this.currency = currency;
    }

    public static Money of(long amount, String currency) {
        return new Money(amount, currency);
    }

    public long getAmount() {
        return amount;
    }

    public void setAmount(long amount) {
        this.amount = amount;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public BigDecimal toBigDecimal() {
        return BigDecimal.valueOf(amount).divide(BigDecimal.valueOf(100), 2, RoundingMode.DOWN);
    }

    @Override
    public String toString() {
        return currency + " " + toBigDecimal();
    }
}