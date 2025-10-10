package com.example.payment.service;

import com.example.order.domain.Order;
import com.example.order.repository.OrderRepository;
import com.example.payment.domain.Payment;
import com.example.payment.repository.PaymentRepository;
import com.example.common.domain.Money;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@Transactional
public class PaymentService {
    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;

    @Autowired
    public PaymentService(PaymentRepository paymentRepository, OrderRepository orderRepository) {
        this.paymentRepository = paymentRepository;
        this.orderRepository = orderRepository;
    }

    /**
     интеграция с лайф пэй, сейчас записывает факт платежа
     */
    public Payment processPayment(UUID orderId, Money amount, String method) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found: " + orderId));
        // record payment
        Payment payment = new Payment(orderId, amount, method, "COMPLETED");
        payment = paymentRepository.save(payment);
        // update order
        order.setPaymentId(payment.getId());
        order.setStatus("PAID");
        orderRepository.save(order);
        return payment;
    }
}