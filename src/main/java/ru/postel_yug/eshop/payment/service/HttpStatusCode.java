package ru.postel_yug.eshop.payment.service;

public enum HttpStatusCode {
    OK(200),
    BAD_REQUEST(400),
    PAYMENT_REQUIRED(402),
    INTERNAL_SERVER_ERROR(500);

    private final int value;

    HttpStatusCode(int value) {
        this.value = value;
    }

    public int value() {
        return value;
    }
}

