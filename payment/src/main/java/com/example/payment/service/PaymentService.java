package com.example.payment.service;

import com.example.order.domain.Order;
import com.example.order.repository.OrderRepository;
import com.example.payment.domain.Payment;
import com.example.payment.domain.PaymentStatus;
import com.example.payment.repository.PaymentRepository;
import com.example.common.domain.Money;
import com.example.order.domain.OrderItem;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

@Service
@Transactional
public class PaymentService {
    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final LifePayClient lifePayClient;
    private final YooKassaClient yooKassaClient;

    @Autowired
    public PaymentService(PaymentRepository paymentRepository, OrderRepository orderRepository,
                          ObjectProvider<LifePayClient> lifePayClientProvider,
                          ObjectProvider<YooKassaClient> yooKassaClientProvider) {
        this.paymentRepository = paymentRepository;
        this.orderRepository = orderRepository;
        this.lifePayClient = lifePayClientProvider.getIfAvailable();
        this.yooKassaClient = yooKassaClientProvider.getIfAvailable();
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

    public Payment createYooKassaPayment(UUID orderId, String receiptEmail, String returnUrl, String idempotencyKey) {
        if (yooKassaClient == null) {
            throw new IllegalStateException("YooKassa integration is disabled (set yookassa.enabled=true to enable).");
        }
        Order order = orderRepository.findWithItemsById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found: " + orderId));

        String resolvedEmail = (receiptEmail != null && !receiptEmail.isBlank())
                ? receiptEmail
                : order.getReceiptEmail();
        if (resolvedEmail == null || resolvedEmail.isBlank()) {
            throw new IllegalArgumentException("Receipt email is required for YooKassa payments");
        }
        if (resolvedEmail != null && !resolvedEmail.isBlank() && (order.getReceiptEmail() == null || order.getReceiptEmail().isBlank())) {
            order.setReceiptEmail(resolvedEmail);
        }

        YooKassaClient.CreatePaymentRequest request = new YooKassaClient.CreatePaymentRequest();
        request.amount = YooKassaClient.Amount.of(formatAmount(order.getTotalAmount()), order.getTotalAmount().getCurrency());
        request.capture = true;
        request.description = "Order " + order.getId();
        request.confirmation = YooKassaClient.Confirmation.redirect(returnUrl);
        request.metadata = YooKassaClient.Metadata.of(order.getId().toString(), order.getPublicToken());
        request.receipt = buildReceipt(order, resolvedEmail);

        YooKassaClient.CreatePaymentResponse response = yooKassaClient.createPayment(request, idempotencyKey);
        if (response == null || response.id == null) {
            throw new PaymentProcessingException("Failed to create YooKassa payment");
        }
        Payment payment = new Payment(order.getId(), order.getTotalAmount(), "YOOKASSA", PaymentStatus.PENDING);
        payment.setProviderPaymentId(response.id);
        payment.setConfirmationUrl(response.confirmation != null ? response.confirmation.confirmationUrl : null);
        payment.setStatus(mapYooKassaStatus(response.status));
        payment = paymentRepository.save(payment);

        order.setPaymentId(payment.getId());
        if (payment.getStatus() == PaymentStatus.COMPLETED) {
            order.setStatus("PAID");
        }
        orderRepository.save(order);
        return payment;
    }

    public Payment refreshYooKassaPayment(String providerPaymentId) {
        if (yooKassaClient == null) {
            throw new IllegalStateException("YooKassa integration is disabled (set yookassa.enabled=true to enable).");
        }
        YooKassaClient.CreatePaymentResponse response = yooKassaClient.getPayment(providerPaymentId);
        if (response == null) {
            throw new IllegalStateException("Failed to fetch YooKassa payment: " + providerPaymentId);
        }
        Payment payment = paymentRepository.findByProviderPaymentId(providerPaymentId)
                .orElseThrow(() -> new IllegalArgumentException("Payment not found: " + providerPaymentId));

        PaymentStatus nextStatus = mapYooKassaStatus(response.status);
        PaymentStatus previousStatus = payment.getStatus();
        if (payment.getStatus() != nextStatus) {
            payment.setStatus(nextStatus);
        }
        if (response.confirmation != null && response.confirmation.confirmationUrl != null) {
            payment.setConfirmationUrl(response.confirmation.confirmationUrl);
        }
        payment = paymentRepository.save(payment);

        Order order = orderRepository.findById(payment.getOrderId())
                .orElseThrow(() -> new IllegalArgumentException("Order not found: " + payment.getOrderId()));
        if (nextStatus == PaymentStatus.COMPLETED) {
            order.setStatus("PAID");
        } else if (nextStatus == PaymentStatus.CANCELLED || nextStatus == PaymentStatus.FAILED) {
            order.setStatus("CANCELLED");
        }
        orderRepository.save(order);
        return payment;
    }

    public PaymentUpdateResult refreshYooKassaPaymentWithResult(String providerPaymentId) {
        if (yooKassaClient == null) {
            throw new IllegalStateException("YooKassa integration is disabled (set yookassa.enabled=true to enable).");
        }
        YooKassaClient.CreatePaymentResponse response = yooKassaClient.getPayment(providerPaymentId);
        if (response == null) {
            throw new IllegalStateException("Failed to fetch YooKassa payment: " + providerPaymentId);
        }
        Payment payment = paymentRepository.findByProviderPaymentId(providerPaymentId)
                .orElseThrow(() -> new IllegalArgumentException("Payment not found: " + providerPaymentId));
        PaymentStatus previousStatus = payment.getStatus();
        PaymentStatus nextStatus = mapYooKassaStatus(response.status);

        if (payment.getStatus() != nextStatus) {
            payment.setStatus(nextStatus);
        }
        if (response.confirmation != null && response.confirmation.confirmationUrl != null) {
            payment.setConfirmationUrl(response.confirmation.confirmationUrl);
        }
        payment = paymentRepository.save(payment);

        Order order = orderRepository.findById(payment.getOrderId())
                .orElseThrow(() -> new IllegalArgumentException("Order not found: " + payment.getOrderId()));
        if (nextStatus == PaymentStatus.COMPLETED) {
            order.setStatus("PAID");
        } else if (nextStatus == PaymentStatus.CANCELLED || nextStatus == PaymentStatus.FAILED) {
            order.setStatus("CANCELLED");
        }
        orderRepository.save(order);
        boolean completedNow = previousStatus != PaymentStatus.COMPLETED && nextStatus == PaymentStatus.COMPLETED;
        return new PaymentUpdateResult(payment, completedNow);
    }

    public record PaymentUpdateResult(Payment payment, boolean completedNow) {}

    private YooKassaClient.Receipt buildReceipt(Order order, String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        YooKassaClient.Receipt receipt = new YooKassaClient.Receipt();
        receipt.customer = new YooKassaClient.ReceiptCustomer();
        receipt.customer.email = email;
        receipt.items = order.getItems().stream()
                .map(item -> {
                    YooKassaClient.ReceiptItem receiptItem = new YooKassaClient.ReceiptItem();
                    receiptItem.description = buildItemDescription(item);
                    receiptItem.quantity = BigDecimal.valueOf(item.getQuantity());
                    receiptItem.amount = YooKassaClient.Amount.of(formatAmount(item.getUnitPrice()), item.getUnitPrice().getCurrency());
                    receiptItem.vatCode = yooKassaClient.getVatCode();
                    receiptItem.paymentMode = "full_payment";
                    receiptItem.paymentSubject = "commodity";
                    return receiptItem;
                })
                .toList();
        receipt.taxSystemCode = yooKassaClient.getTaxSystemCode();
        return receipt;
    }

    private String buildItemDescription(OrderItem item) {
        String product = (item.getProductName() != null && !item.getProductName().isBlank())
                ? item.getProductName()
                : "Item";
        String variant = (item.getVariantName() != null && !item.getVariantName().isBlank())
                ? " (" + item.getVariantName() + ")"
                : "";
        return product + variant;
    }

    private PaymentStatus mapYooKassaStatus(String status) {
        if (status == null) return PaymentStatus.PENDING;
        return switch (status.toLowerCase()) {
            case "pending", "waiting_for_capture" -> PaymentStatus.PENDING;
            case "succeeded" -> PaymentStatus.COMPLETED;
            case "canceled" -> PaymentStatus.CANCELLED;
            default -> PaymentStatus.FAILED;
        };
    }

    private String formatAmount(Money money) {
        BigDecimal decimal = money.toBigDecimal();
        return decimal.setScale(2, RoundingMode.HALF_UP).toString();
    }
}
