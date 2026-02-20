package com.example.api.payment;

import com.example.payment.domain.Payment;
import com.example.payment.service.PaymentService;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.example.api.notification.EmailService;
import com.example.order.domain.Order;
import com.example.order.service.OrderService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/payments")
public class PaymentController {

    private final PaymentService paymentService;
    private final OrderService orderService;
    private final EmailService emailService;
    private final YooKassaWebhookVerifier webhookVerifier;

    @Autowired
    public PaymentController(PaymentService paymentService,
                             OrderService orderService,
                             EmailService emailService,
                             YooKassaWebhookVerifier webhookVerifier) {
        this.paymentService = paymentService;
        this.orderService = orderService;
        this.emailService = emailService;
        this.webhookVerifier = webhookVerifier;
    }

    @PostMapping("/yookassa/webhook")
    public ResponseEntity<Void> handleYooKassaWebhook(@RequestBody YooKassaNotification notification,
                                                      HttpServletRequest request) {
        return handleYooKassaNotification(notification, request);
    }

    @PostMapping("/yookassa/refund")
    public ResponseEntity<Payment> refundYooKassaPayment(@Valid @RequestBody RefundRequest request) {
        Order beforeUpdate = orderService.findById(request.getOrderId());
        Payment payment = paymentService.refundYooKassaPayment(request.getOrderId());
        Order updated = orderService.findById(payment.getOrderId());
        notifyStatusChangeIfNeeded(updated, beforeUpdate.getStatus());
        return ResponseEntity.ok(payment);
    }

    @PostMapping("/yookassa/cancel")
    public ResponseEntity<Payment> cancelYooKassaPayment(@Valid @RequestBody CancelRequest request) {
        Order beforeUpdate = orderService.findById(request.getOrderId());
        Payment payment = paymentService.cancelYooKassaPayment(request.getOrderId());
        Order updated = orderService.findById(payment.getOrderId());
        notifyStatusChangeIfNeeded(updated, beforeUpdate.getStatus());
        return ResponseEntity.ok(payment);
    }

    public static class RefundRequest {
        @NotNull
        private UUID orderId;

        public UUID getOrderId() {
            return orderId;
        }

        public void setOrderId(UUID orderId) {
            this.orderId = orderId;
        }
    }

    public static class CancelRequest {
        @NotNull
        private UUID orderId;

        public UUID getOrderId() {
            return orderId;
        }

        public void setOrderId(UUID orderId) {
            this.orderId = orderId;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class YooKassaNotification {
        public String event;
        public YooKassaEventObject object;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class YooKassaEventObject {
        public String id;
        public String status;
        @com.fasterxml.jackson.annotation.JsonProperty("payment_id")
        public String paymentId;
    }

    private ResponseEntity<Void> handleYooKassaNotification(YooKassaNotification notification,
                                                            HttpServletRequest request) {
        if (!webhookVerifier.isValid(request)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        if (notification == null || notification.object == null || notification.object.id == null) {
            return ResponseEntity.badRequest().build();
        }
        try {
            String event = notification.event != null ? notification.event : "";
            if (event.startsWith("payment_method")) {
                paymentService.handlePaymentMethodActiveWebhook(notification.object.id);
                return ResponseEntity.ok().build();
            }
            if (event.startsWith("refund")) {
                String previousStatus = resolveOrderStatusByProviderPaymentId(notification.object.paymentId);
                Payment payment = paymentService.handleYooKassaRefundWebhook(
                        notification.object.id,
                        notification.object.paymentId
                );
                Order order = orderService.findById(payment.getOrderId());
                notifyStatusChangeIfNeeded(order, previousStatus);
                return ResponseEntity.ok().build();
            }
            if (event.startsWith("payment")) {
                String previousStatus = resolveOrderStatusByProviderPaymentId(notification.object.id);
                PaymentService.PaymentUpdateResult result =
                        paymentService.refreshYooKassaPaymentWithResult(notification.object.id);
                Payment payment = result.payment();
                Order order = orderService.findById(payment.getOrderId());
                notifyStatusChangeIfNeeded(order, previousStatus);
                if (result.completedNow()) {
                    String email = order.getReceiptEmail();
                    if (StringUtils.hasText(email)) {
                        emailService.sendPaymentReceipt(order, payment, email);
                    }
                }
                return ResponseEntity.ok().build();
            }
        } catch (RuntimeException ex) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.badRequest().build();
    }

    private String resolveOrderStatusByProviderPaymentId(String providerPaymentId) {
        Payment payment = paymentService.findByProviderPaymentId(providerPaymentId);
        if (payment == null) {
            return null;
        }
        Order order = orderService.findById(payment.getOrderId());
        return order.getStatus();
    }

    private void notifyStatusChangeIfNeeded(Order order, String previousStatus) {
        if (order == null || !StringUtils.hasText(order.getReceiptEmail())) {
            return;
        }
        String nextStatus = order.getStatus();
        if (!StringUtils.hasText(previousStatus) || !StringUtils.hasText(nextStatus)) {
            return;
        }
        if (previousStatus.equalsIgnoreCase(nextStatus)) {
            return;
        }
        emailService.sendOrderStatusUpdatedEmail(order, order.getReceiptEmail(), previousStatus);
    }
}
