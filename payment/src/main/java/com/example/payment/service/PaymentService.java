package com.example.payment.service;

import com.example.order.domain.Order;
import com.example.order.repository.OrderRepository;
import com.example.payment.domain.Payment;
import com.example.payment.domain.PaymentStatus;
import com.example.payment.repository.PaymentRepository;
import com.example.common.domain.Money;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@Transactional
public class PaymentService {
    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final LifePayClient lifePayClient;

    @Autowired
    public PaymentService(PaymentRepository paymentRepository, OrderRepository orderRepository,
                          ObjectProvider<LifePayClient> lifePayClientProvider) {
        this.paymentRepository = paymentRepository;
        this.orderRepository = orderRepository;
        this.lifePayClient = lifePayClientProvider.getIfAvailable();
    }

    public Payment processPayment(UUID orderId, Money amount, String method, String cardToken, BrowserInfo browserInfo) {
        if (lifePayClient == null) {
            throw new IllegalStateException("LifePay integration is disabled (set lifepay.enabled=true to enable).");
        }
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found: " + orderId));

        Payment payment = new Payment(orderId, amount, method, PaymentStatus.PENDING);

        try {
            String jwt = lifePayClient.authenticate();

            String orderRef = orderId.toString();
            String currencyCode = amount.getCurrency();
            String amountStr = formatAmount(amount);
            LifePayClient.LifePayInvoiceResponse invoiceResp = lifePayClient.createInvoice(
                    jwt, orderRef, amountStr, currencyCode, "Order " + orderRef,
                    /* email */ null, /* phone */ null);
            if (invoiceResp == null || invoiceResp.id == null) {
                throw new PaymentProcessingException("Failed to create payment invoice in LifePay");
            }
            UUID invoiceId = invoiceResp.id;

            LifePayClient.LifePayTokenResponse tokenResp = lifePayClient.createPaymentToken(jwt, invoiceId, cardToken);
            if (tokenResp == null || tokenResp.id == null) {
                String errMsg = (tokenResp != null ? tokenResp.message : "unknown error");
                throw new PaymentProcessingException("Card tokenization failed: " + errMsg);
            }
            UUID paymentTokenId = tokenResp.id;

            LifePayClient.LifePayChargeResponse chargeResp = lifePayClient.createCharge(jwt, invoiceId, paymentTokenId, browserInfo);
            if (chargeResp == null || chargeResp.id == null) {
                String errMsg = (chargeResp != null ? chargeResp.message : "no response");
                throw new PaymentProcessingException("Payment charge request failed: " + errMsg);
            }
            String lifePayStatus = chargeResp.status;

            if ("progress".equalsIgnoreCase(lifePayStatus) || "waiting_3ds".equalsIgnoreCase(lifePayStatus)) {
                payment.setStatus(PaymentStatus.PENDING);
            } else if ("charged".equalsIgnoreCase(lifePayStatus)) {
                payment.setStatus(PaymentStatus.COMPLETED);
                order.setStatus("PAID");
            } else if ("error".equalsIgnoreCase(lifePayStatus)) {
                payment.setStatus(PaymentStatus.FAILED);
            } else if ("blocked".equalsIgnoreCase(lifePayStatus)) {
                payment.setStatus(PaymentStatus.PENDING);
            } else {
                payment.setStatus(PaymentStatus.FAILED);
            }

            payment = paymentRepository.save(payment);
            order.setPaymentId(payment.getId());
            orderRepository.save(order);

            if (payment.getStatus() == PaymentStatus.FAILED) {
                throw new PaymentProcessingException("Payment unsuccessful (LifePay status: " + lifePayStatus + ")");
            }

        } catch (PaymentProcessingException e) {
            payment.setStatus(PaymentStatus.FAILED);
            paymentRepository.save(payment);
            throw e;
        }

        return payment;
    }

    private String formatAmount(Money money) {
        java.math.BigDecimal decimal = money.toBigDecimal();
        return decimal.setScale(2, java.math.RoundingMode.HALF_UP).toString();
    }
}
