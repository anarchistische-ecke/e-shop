package com.example.payment.service;

import com.example.order.domain.Order;

public interface PromoCodeRedemptionRecorder {
    void recordPaidOrder(Order order);
}
