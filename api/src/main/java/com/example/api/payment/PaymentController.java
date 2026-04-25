package com.example.api.payment;

import com.example.payment.domain.Payment;
import com.example.payment.service.PaymentService;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.example.api.notification.NotificationOrchestrator;
import com.example.order.domain.Order;
import com.example.order.service.OrderService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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
    private final NotificationOrchestrator notificationOrchestrator;
    private final YooKassaWebhookVerifier webhookVerifier;

    @Value("${yookassa.enabled:false}")
    private boolean yooKassaEnabled;

    @Value("${payment.public.provider-code:YOOKASSA}")
    private String publicProviderCode;

    @Value("${payment.public.provider-name:YooKassa}")
    private String publicProviderName;

    @Value("${payment.public.method-summary:карта / SberPay}")
    private String publicMethodSummary;

    @Value("${payment.public.checkout-description:}")
    private String publicCheckoutDescription;

    @Value("${payment.public.resume-payment-label:}")
    private String publicResumePaymentLabel;

    @Value("${payment.public.confirmation-mode:REDIRECT}")
    private String publicConfirmationMode;

    @Value("${payment.public.widget-script-url:https://yookassa.ru/checkout-widget/v1/checkout-widget.js}")
    private String publicWidgetScriptUrl;

    @Autowired
    public PaymentController(PaymentService paymentService,
                             OrderService orderService,
                             NotificationOrchestrator notificationOrchestrator,
                             YooKassaWebhookVerifier webhookVerifier) {
        this.paymentService = paymentService;
        this.orderService = orderService;
        this.notificationOrchestrator = notificationOrchestrator;
        this.webhookVerifier = webhookVerifier;
    }

    @GetMapping("/public-config")
    public ResponseEntity<PublicPaymentConfig> getPublicConfig() {
        String confirmationMode = resolveConfirmationMode(publicConfirmationMode);
        String providerName = safeValue(publicProviderName, "YooKassa");
        return ResponseEntity.ok(new PublicPaymentConfig(
                yooKassaEnabled,
                safeValue(publicProviderCode, "YOOKASSA"),
                providerName,
                safeValue(publicMethodSummary, "карта / SberPay"),
                resolveCheckoutDescription(providerName, confirmationMode),
                resolveResumePaymentLabel(providerName, confirmationMode),
                confirmationMode,
                yooKassaEnabled,
                safeValue(publicWidgetScriptUrl, "https://yookassa.ru/checkout-widget/v1/checkout-widget.js")
        ));
    }

    @PostMapping("/yookassa/webhook")
    public ResponseEntity<Void> handleYooKassaWebhook(@RequestBody YooKassaNotification notification,
                                                      HttpServletRequest request) {
        return handleYooKassaNotification(notification, request);
    }

    @PostMapping("/yookassa/refund")
    public ResponseEntity<Payment> refundYooKassaPayment(@Valid @RequestBody RefundRequest request) {
        Payment payment = paymentService.refundYooKassaPayment(request.getOrderId());
        return ResponseEntity.ok(payment);
    }

    @PostMapping("/yookassa/cancel")
    public ResponseEntity<Payment> cancelYooKassaPayment(@Valid @RequestBody CancelRequest request) {
        Payment payment = paymentService.cancelYooKassaPayment(request.getOrderId());
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
                paymentService.handleYooKassaRefundWebhook(
                        notification.object.id,
                        notification.object.paymentId
                );
                return ResponseEntity.ok().build();
            }
            if (event.startsWith("payment")) {
                PaymentService.PaymentUpdateResult result =
                        paymentService.refreshYooKassaPaymentWithResult(notification.object.id);
                Payment payment = result.payment();
                Order order = orderService.findById(payment.getOrderId());
                if (result.completedNow()) {
                    notificationOrchestrator.orderPaid(order, payment);
                }
                return ResponseEntity.ok().build();
            }
        } catch (RuntimeException ex) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.badRequest().build();
    }

    private String safeValue(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }

    private String resolveConfirmationMode(String confirmationMode) {
        return "EMBEDDED".equalsIgnoreCase(confirmationMode) ? "EMBEDDED" : "REDIRECT";
    }

    private String resolveCheckoutDescription(String providerName, String confirmationMode) {
        if (StringUtils.hasText(publicCheckoutDescription)) {
            return publicCheckoutDescription.trim();
        }
        if ("EMBEDDED".equalsIgnoreCase(confirmationMode)) {
            return "Оплата во встроенной защищённой форме " + providerName + ". Данные карты не хранятся в браузере магазина.";
        }
        return "Оплата на защищённой странице " + providerName + ". Данные карты не хранятся в браузере магазина.";
    }

    private String resolveResumePaymentLabel(String providerName, String confirmationMode) {
        if (StringUtils.hasText(publicResumePaymentLabel)) {
            return publicResumePaymentLabel.trim();
        }
        if ("EMBEDDED".equalsIgnoreCase(confirmationMode)) {
            return "Открыть форму оплаты через " + providerName;
        }
        return "Продолжить оплату через " + providerName;
    }

    public record PublicPaymentConfig(
            boolean enabled,
            String providerCode,
            String providerName,
            String methodSummary,
            String checkoutDescription,
            String resumePaymentLabel,
            String confirmationMode,
            boolean supportsEmbedded,
            String widgetScriptUrl
    ) {}
}
