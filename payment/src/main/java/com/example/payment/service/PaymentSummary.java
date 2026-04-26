package com.example.payment.service;

import com.example.common.domain.Money;
import com.example.payment.domain.PaymentStatus;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record PaymentSummary(
        UUID paymentId,
        String providerPaymentId,
        String method,
        PaymentStatus status,
        Money amount,
        String receiptRegistration,
        String receiptUrl,
        Money refundedAmount,
        Money refundableAmount,
        List<RefundSummary> refunds
) {
    public record RefundSummary(
            UUID id,
            String refundId,
            String status,
            Money amount,
            OffsetDateTime refundDate,
            List<RefundItemSummary> items
    ) {
    }

    public record RefundItemSummary(
            UUID orderItemId,
            int quantity,
            Money amount,
            String status
    ) {
    }
}
