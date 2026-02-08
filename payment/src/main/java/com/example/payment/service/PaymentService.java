package com.example.payment.service;

import com.example.common.domain.Money;
import com.example.delivery.YandexDeliveryClient;
import com.example.customer.domain.Customer;
import com.example.customer.repository.CustomerRepository;
import com.example.order.domain.Order;
import com.example.order.domain.OrderItem;
import com.example.order.repository.OrderRepository;
import com.example.order.service.OrderService;
import com.example.payment.domain.Payment;
import com.example.payment.domain.PaymentStatus;
import com.example.payment.domain.PaymentRefund;
import com.example.payment.domain.SavedPaymentMethod;
import com.example.payment.repository.PaymentRefundRepository;
import com.example.payment.repository.PaymentRepository;
import com.example.payment.repository.SavedPaymentMethodRepository;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class PaymentService {
    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final PaymentRepository paymentRepository;
    private final PaymentRefundRepository paymentRefundRepository;
    private final SavedPaymentMethodRepository savedPaymentMethodRepository;
    private final OrderRepository orderRepository;
    private final CustomerRepository customerRepository;
    private final YooKassaClient yooKassaClient;
    private final OrderService orderService;
    private final YandexDeliveryClient yandexDeliveryClient;

    @Autowired
    public PaymentService(PaymentRepository paymentRepository,
                          PaymentRefundRepository paymentRefundRepository,
                          SavedPaymentMethodRepository savedPaymentMethodRepository,
                          OrderRepository orderRepository,
                          ObjectProvider<CustomerRepository> customerRepositoryProvider,
                          ObjectProvider<YooKassaClient> yooKassaClientProvider,
                          ObjectProvider<OrderService> orderServiceProvider,
                          ObjectProvider<YandexDeliveryClient> yandexDeliveryClientProvider) {
        this.paymentRepository = paymentRepository;
        this.paymentRefundRepository = paymentRefundRepository;
        this.savedPaymentMethodRepository = savedPaymentMethodRepository;
        this.orderRepository = orderRepository;
        this.customerRepository = customerRepositoryProvider.getIfAvailable();
        this.yooKassaClient = yooKassaClientProvider.getIfAvailable();
        this.orderService = orderServiceProvider.getIfAvailable();
        this.yandexDeliveryClient = yandexDeliveryClientProvider.getIfAvailable();
    }

    public Payment createYooKassaPayment(UUID orderId,
                                         String receiptEmail,
                                         String returnUrl,
                                         String idempotencyKey,
                                         Boolean savePaymentMethod,
                                         String merchantCustomerId) {
        if (yooKassaClient == null) {
            throw new IllegalStateException("YooKassa integration is disabled (set yookassa.enabled=true to enable).");
        }
        Order order = orderRepository.findWithItemsById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found: " + orderId));
        if (isOrderRefunded(order)) {
            throw new IllegalStateException("Order has been refunded and cannot be paid");
        }

        String resolvedEmail = (receiptEmail != null && !receiptEmail.isBlank())
                ? receiptEmail
                : order.getReceiptEmail();
        if (resolvedEmail == null || resolvedEmail.isBlank()) {
            throw new IllegalArgumentException("Receipt email is required for YooKassa payments");
        }
        if (resolvedEmail != null && !resolvedEmail.isBlank() && (order.getReceiptEmail() == null || order.getReceiptEmail().isBlank())) {
            order.setReceiptEmail(resolvedEmail);
            orderRepository.save(order);
        }

        Payment latest = paymentRepository.findTopByOrderIdOrderByPaymentDateDesc(orderId).orElse(null);
        if (isOrderPaid(order)) {
            if (latest != null && latest.getStatus() == PaymentStatus.COMPLETED) {
                syncOrderWithPayment(order, latest, latest.getStatus());
                return latest;
            }
            throw new IllegalStateException("Order already paid");
        }
        if (latest != null) {
            if (latest.getStatus() == PaymentStatus.PENDING && latest.getConfirmationUrl() != null) {
                syncOrderWithPayment(order, latest, latest.getStatus());
                return latest;
            }
            if (latest.getStatus() == PaymentStatus.COMPLETED) {
                syncOrderWithPayment(order, latest, latest.getStatus());
                return latest;
            }
        }

        String effectiveKey = resolveIdempotencyKey(orderId, latest, idempotencyKey);

        YooKassaClient.CreatePaymentRequest request = new YooKassaClient.CreatePaymentRequest();
        request.amount = YooKassaClient.Amount.of(formatAmount(order.getTotalAmount()), order.getTotalAmount().getCurrency());
        request.capture = true;
        request.description = "Order " + order.getId();
        request.confirmation = YooKassaClient.Confirmation.redirect(returnUrl);
        request.metadata = YooKassaClient.Metadata.of(order.getId().toString(), order.getPublicToken());
        request.receipt = buildReceipt(order, resolvedEmail);
        if (Boolean.TRUE.equals(savePaymentMethod)) {
            request.savePaymentMethod = true;
            if (merchantCustomerId != null && !merchantCustomerId.isBlank()) {
                request.merchantCustomerId = merchantCustomerId;
            }
        }

        YooKassaClient.CreatePaymentResponse response = yooKassaClient.createPayment(request, effectiveKey);
        if (response == null || response.id == null) {
            throw new PaymentProcessingException("Failed to create YooKassa payment");
        }
        Payment payment = new Payment(order.getId(), order.getTotalAmount(), "YOOKASSA", PaymentStatus.PENDING);
        payment.setProviderPaymentId(response.id);
        payment.setConfirmationUrl(response.confirmation != null ? response.confirmation.confirmationUrl : null);
        payment.setStatus(mapYooKassaStatus(response.status));
        payment = paymentRepository.save(payment);

        syncOrderWithPayment(order, payment, payment.getStatus());
        persistSavedPaymentMethod(order, response);
        return payment;
    }

    public Payment refreshYooKassaPayment(String providerPaymentId) {
        return refreshYooKassaPaymentWithResult(providerPaymentId).payment();
    }

    public PaymentUpdateResult refreshYooKassaPaymentWithResult(String providerPaymentId) {
        if (yooKassaClient == null) {
            throw new IllegalStateException("YooKassa integration is disabled (set yookassa.enabled=true to enable).");
        }
        return refreshYooKassaPaymentInternal(providerPaymentId);
    }

    private PaymentUpdateResult refreshYooKassaPaymentInternal(String providerPaymentId) {
        YooKassaClient.CreatePaymentResponse response = yooKassaClient.getPayment(providerPaymentId);
        if (response == null) {
            throw new IllegalStateException("Failed to fetch YooKassa payment: " + providerPaymentId);
        }
        if (response.id != null && !response.id.equals(providerPaymentId)) {
            throw new IllegalStateException("YooKassa payment id mismatch");
        }
        Payment payment = paymentRepository.findByProviderPaymentId(providerPaymentId)
                .orElseThrow(() -> new IllegalArgumentException("Payment not found: " + providerPaymentId));
        UUID orderId = payment.getOrderId();
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found: " + orderId));
        assertMetadataMatches(response, order);
        assertAmountMatches(response, order);

        PaymentStatus previousStatus = payment.getStatus();
        if (previousStatus != PaymentStatus.REFUNDED) {
            PaymentStatus nextStatus = mapYooKassaStatus(response.status);
            if (previousStatus != nextStatus) {
                payment.setStatus(nextStatus);
            }
            if (response.confirmation != null && response.confirmation.confirmationUrl != null) {
                payment.setConfirmationUrl(response.confirmation.confirmationUrl);
            }
            payment = paymentRepository.save(payment);
            persistSavedPaymentMethod(order, response);

            boolean isCurrent = isCurrentPayment(order, payment);
            if (isCurrent) {
                syncOrderWithPayment(order, payment, payment.getStatus());
            }
            boolean completedNow = isCurrent
                    && previousStatus != PaymentStatus.COMPLETED
                    && payment.getStatus() == PaymentStatus.COMPLETED;
            return new PaymentUpdateResult(payment, completedNow);
        }

        return new PaymentUpdateResult(payment, false);
    }

    public record PaymentUpdateResult(Payment payment, boolean completedNow) {}

    public PaymentUpdateResult refreshLatestYooKassaPaymentForOrder(UUID orderId) {
        Payment latest = paymentRepository.findTopByOrderIdOrderByPaymentDateDesc(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Payment not found for order: " + orderId));
        if (latest.getProviderPaymentId() == null || latest.getProviderPaymentId().isBlank()) {
            throw new IllegalArgumentException("Provider payment id missing for order: " + orderId);
        }
        return refreshYooKassaPaymentWithResult(latest.getProviderPaymentId());
    }

    public Payment cancelYooKassaPayment(UUID orderId) {
        if (yooKassaClient == null) {
            throw new IllegalStateException("YooKassa integration is disabled (set yookassa.enabled=true to enable).");
        }
        Payment payment = paymentRepository.findTopByOrderIdOrderByPaymentDateDesc(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Payment not found for order: " + orderId));
        if (payment.getProviderPaymentId() == null || payment.getProviderPaymentId().isBlank()) {
            throw new IllegalArgumentException("Provider payment id missing for order: " + orderId);
        }
        if (payment.getStatus() == PaymentStatus.CANCELLED) {
            return payment;
        }
        YooKassaClient.CreatePaymentResponse current = yooKassaClient.getPayment(payment.getProviderPaymentId());
        if (current == null || current.status == null) {
            throw new PaymentProcessingException("Failed to verify YooKassa payment status before cancel");
        }
        if (!"waiting_for_capture".equalsIgnoreCase(current.status)) {
            throw new IllegalStateException("Only payments waiting_for_capture can be cancelled");
        }

        YooKassaClient.CreatePaymentResponse response = yooKassaClient.cancelPayment(
                payment.getProviderPaymentId(),
                buildCancelIdempotencyKey(payment)
        );
        if (response == null || response.id == null) {
            throw new PaymentProcessingException("Failed to cancel YooKassa payment");
        }
        PaymentStatus nextStatus = mapYooKassaStatus(response.status);
        if (nextStatus != PaymentStatus.CANCELLED) {
            throw new PaymentProcessingException("YooKassa payment cancel did not complete (status: " + response.status + ")");
        }
        payment.setStatus(PaymentStatus.CANCELLED);
        paymentRepository.save(payment);

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found: " + orderId));
        if (isCurrentPayment(order, payment)) {
            syncOrderWithPayment(order, payment, payment.getStatus());
        }
        return payment;
    }

    public Payment handleYooKassaRefundWebhook(String refundId, String paymentIdHint) {
        if (yooKassaClient == null) {
            throw new IllegalStateException("YooKassa integration is disabled (set yookassa.enabled=true to enable).");
        }
        if (!StringUtils.hasText(refundId)) {
            throw new IllegalArgumentException("Refund id is required");
        }
        YooKassaClient.RefundResponse response = yooKassaClient.getRefund(refundId);
        if (response == null || response.id == null) {
            throw new PaymentProcessingException("Failed to fetch YooKassa refund");
        }
        if (StringUtils.hasText(paymentIdHint) && response.paymentId != null && !response.paymentId.equals(paymentIdHint)) {
            throw new IllegalStateException("YooKassa refund payment id mismatch");
        }
        if (!StringUtils.hasText(response.paymentId)) {
            throw new IllegalStateException("YooKassa refund missing payment id");
        }
        Payment payment = paymentRepository.findByProviderPaymentId(response.paymentId)
                .orElseThrow(() -> new IllegalArgumentException("Payment not found: " + response.paymentId));
        Order order = orderRepository.findById(payment.getOrderId())
                .orElseThrow(() -> new IllegalArgumentException("Order not found: " + payment.getOrderId()));
        assertRefundMetadataMatches(response, order);
        return applyRefundResponse(payment, order, response);
    }

    public void handlePaymentMethodActiveWebhook(String paymentMethodId) {
        if (yooKassaClient == null || savedPaymentMethodRepository == null) {
            return;
        }
        if (!StringUtils.hasText(paymentMethodId)) {
            return;
        }
        YooKassaClient.PaymentMethod method = yooKassaClient.getPaymentMethod(paymentMethodId);
        if (method == null || method.id == null) {
            return;
        }
        SavedPaymentMethod existing = savedPaymentMethodRepository
                .findByProviderPaymentMethodId(method.id)
                .orElse(null);
        if (existing == null) {
            return;
        }
        applyPaymentMethodDetails(existing, method);
        savedPaymentMethodRepository.save(existing);
    }

    public Payment refundYooKassaPayment(UUID orderId) {
        if (yooKassaClient == null) {
            throw new IllegalStateException("YooKassa integration is disabled (set yookassa.enabled=true to enable).");
        }
        Payment payment = paymentRepository.findTopByOrderIdOrderByPaymentDateDesc(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Payment not found for order: " + orderId));
        if (payment.getProviderPaymentId() == null || payment.getProviderPaymentId().isBlank()) {
            throw new IllegalArgumentException("Provider payment id missing for order: " + orderId);
        }
        if (payment.getStatus() != PaymentStatus.COMPLETED) {
            throw new IllegalStateException("Only completed payments can be refunded");
        }
        Order order = orderRepository.findWithItemsById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found: " + orderId));

        YooKassaClient.CreateRefundRequest request = new YooKassaClient.CreateRefundRequest();
        request.paymentId = payment.getProviderPaymentId();
        request.amount = YooKassaClient.Amount.of(formatAmount(payment.getAmount()), payment.getAmount().getCurrency());
        request.metadata = YooKassaClient.Metadata.of(orderId.toString(), null);
        if (order.getReceiptEmail() != null && !order.getReceiptEmail().isBlank()) {
            request.receipt = buildReceipt(order, order.getReceiptEmail());
        }

        YooKassaClient.RefundResponse response = yooKassaClient.createRefund(
                request,
                buildRefundIdempotencyKey(payment)
        );
        if (response == null || response.id == null) {
            throw new PaymentProcessingException("Failed to create YooKassa refund");
        }
        assertRefundMetadataMatches(response, order);
        return applyRefundResponse(payment, order, response);
    }

    private Payment applyRefundResponse(Payment payment, Order order, YooKassaClient.RefundResponse response) {
        payment.setRefundId(response.id);
        payment.setRefundStatus(normalizeRefundStatus(response.status));
        payment.setRefundAmount(toMoney(response.amount));
        payment.setRefundDate(resolveRefundDate(response));
        payment = paymentRepository.save(payment);

        upsertRefundRecord(payment, response);

        long totalRefunded = Math.max(0L, valueOrZero(
                paymentRefundRepository.sumAmountByPaymentIdAndStatus(payment.getId(), "SUCCEEDED")
        ));
        boolean fullRefund = isFullRefund(payment, totalRefunded);
        if (isRefundSucceeded(response.status) && fullRefund) {
            payment.setStatus(PaymentStatus.REFUNDED);
            payment = paymentRepository.save(payment);
        }

        if (isRefundSucceeded(response.status) && fullRefund && isCurrentPayment(order, payment)) {
            order.setStatus("REFUNDED");
            orderRepository.save(order);
            restockOrder(order.getId(), "PAYMENT_REFUNDED", "restock-refund-" + order.getId());
            cancelYandexDeliveryIfNeeded(order);
        }
        return payment;
    }

    private YooKassaClient.Receipt buildReceipt(Order order, String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        YooKassaClient.Receipt receipt = new YooKassaClient.Receipt();
        receipt.customer = new YooKassaClient.ReceiptCustomer();
        receipt.customer.email = email;
        String phone = resolveReceiptPhone(order, email);
        if (StringUtils.hasText(phone)) {
            receipt.customer.phone = phone;
        }
        List<YooKassaClient.ReceiptItem> receiptItems = new ArrayList<>();
        receiptItems.addAll(order.getItems().stream()
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
                .toList());
        if (order.getDeliveryAmount() != null && order.getDeliveryAmount().getAmount() > 0) {
            YooKassaClient.ReceiptItem deliveryItem = new YooKassaClient.ReceiptItem();
            deliveryItem.description = "Доставка";
            deliveryItem.quantity = BigDecimal.ONE;
            deliveryItem.amount = YooKassaClient.Amount.of(formatAmount(order.getDeliveryAmount()), order.getDeliveryAmount().getCurrency());
            deliveryItem.vatCode = yooKassaClient.getVatCode();
            deliveryItem.paymentMode = "full_payment";
            deliveryItem.paymentSubject = "service";
            receiptItems.add(deliveryItem);
        }
        receipt.items = receiptItems;
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

    private String resolveIdempotencyKey(UUID orderId, Payment latest, String providedKey) {
        if (latest == null) {
            return StringUtils.hasText(providedKey) ? providedKey : buildIdempotencyKey(orderId);
        }
        if (latest.getStatus() == PaymentStatus.CANCELLED || latest.getStatus() == PaymentStatus.FAILED || latest.getStatus() == PaymentStatus.REFUNDED) {
            return buildRetryIdempotencyKey(orderId);
        }
        return StringUtils.hasText(providedKey) ? providedKey : buildIdempotencyKey(orderId);
    }

    private String buildIdempotencyKey(UUID orderId) {
        return "order-" + orderId;
    }

    private String buildRetryIdempotencyKey(UUID orderId) {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        return "order-" + orderId + "-r" + suffix;
    }

    private String buildRefundIdempotencyKey(Payment payment) {
        return "refund-" + payment.getProviderPaymentId();
    }

    private String buildCancelIdempotencyKey(Payment payment) {
        return "cancel-" + payment.getProviderPaymentId();
    }

    private void assertMetadataMatches(YooKassaClient.CreatePaymentResponse response, Order order) {
        if (response == null || response.metadata == null || order == null) {
            return;
        }
        if (response.metadata.orderId != null && !response.metadata.orderId.equals(order.getId().toString())) {
            throw new IllegalStateException("YooKassa metadata mismatch (order_id)");
        }
        if (response.metadata.publicToken != null
                && order.getPublicToken() != null
                && !response.metadata.publicToken.equals(order.getPublicToken())) {
            throw new IllegalStateException("YooKassa metadata mismatch (public_token)");
        }
    }

    private void assertAmountMatches(YooKassaClient.CreatePaymentResponse response, Order order) {
        if (response == null || response.amount == null || response.amount.value == null || response.amount.currency == null) {
            throw new IllegalStateException("YooKassa amount is missing");
        }
        if (order == null || order.getTotalAmount() == null) {
            throw new IllegalStateException("Order total is missing");
        }
        BigDecimal expectedValue = order.getTotalAmount().toBigDecimal().setScale(2, RoundingMode.HALF_UP);
        BigDecimal actualValue;
        try {
            actualValue = new BigDecimal(response.amount.value).setScale(2, RoundingMode.HALF_UP);
        } catch (NumberFormatException ex) {
            throw new IllegalStateException("YooKassa amount format is invalid");
        }
        String expectedCurrency = normalizeCurrency(order.getTotalAmount().getCurrency());
        if (expectedValue.compareTo(actualValue) != 0
                || !expectedCurrency.equals(normalizeCurrency(response.amount.currency))) {
            throw new IllegalStateException("YooKassa amount mismatch");
        }
    }

    private void syncOrderWithPayment(Order order, Payment payment, PaymentStatus status) {
        if (order == null || payment == null || status == null) {
            return;
        }
        String previousStatus = order.getStatus();
        boolean changed = false;
        if (order.getPaymentId() == null || !order.getPaymentId().equals(payment.getId())) {
            order.setPaymentId(payment.getId());
            changed = true;
        }
        if (isOrderRefunded(order)) {
            if (changed) {
                orderRepository.save(order);
            }
            return;
        }
        if (status == PaymentStatus.COMPLETED) {
            if (!isOrderPaid(order)) {
                order.setStatus("PAID");
                changed = true;
            }
        } else if (status == PaymentStatus.CANCELLED || status == PaymentStatus.FAILED) {
            if (!isOrderPaid(order)) {
                order.setStatus("CANCELLED");
                changed = true;
            }
        } else if (!isOrderPaid(order)) {
            if (order.getStatus() == null || !"PENDING".equalsIgnoreCase(order.getStatus())) {
                order.setStatus("PENDING");
                changed = true;
            }
        }
        if (changed) {
            orderRepository.save(order);
        }
        if (changed && isCancelledStatus(order.getStatus()) && !isCancelledStatus(previousStatus)) {
            restockOrder(order.getId(), "PAYMENT_CANCELLED", "restock-cancel-" + order.getId());
            cancelYandexDeliveryIfNeeded(order);
        }
    }

    private boolean isOrderPaid(Order order) {
        return order != null && "PAID".equalsIgnoreCase(order.getStatus());
    }

    private boolean isOrderRefunded(Order order) {
        return order != null && "REFUNDED".equalsIgnoreCase(order.getStatus());
    }

    private long valueOrZero(Long value) {
        return value != null ? value : 0L;
    }

    private boolean isCurrentPayment(Order order, Payment payment) {
        if (order == null || payment == null) {
            return false;
        }
        return order.getPaymentId() == null || order.getPaymentId().equals(payment.getId());
    }

    private String resolveReceiptPhone(Order order, String email) {
        if (customerRepository == null) {
            return null;
        }
        Customer customer = null;
        if (order != null && order.getCustomerId() != null) {
            customer = customerRepository.findById(order.getCustomerId()).orElse(null);
        }
        if (customer == null && StringUtils.hasText(email)) {
            customer = customerRepository.findByEmail(email).orElse(null);
        }
        String phone = customer != null ? customer.getPhone() : null;
        return StringUtils.hasText(phone) ? phone : null;
    }

    private void restockOrder(UUID orderId, String reason, String idempotencyPrefix) {
        if (orderService == null || orderId == null) {
            return;
        }
        orderService.restockOrderItems(orderId, reason, idempotencyPrefix);
    }

    private void cancelYandexDeliveryIfNeeded(Order order) {
        if (order == null || yandexDeliveryClient == null) {
            return;
        }
        String requestId = order.getDeliveryRequestId();
        if (!StringUtils.hasText(requestId)) {
            return;
        }
        try {
            yandexDeliveryClient.cancelRequest(requestId);
            if (!"CANCELLED".equalsIgnoreCase(order.getDeliveryStatus())) {
                order.setDeliveryStatus("CANCELLED");
                orderRepository.save(order);
            }
        } catch (Exception ex) {
            log.warn("Failed to cancel Yandex delivery request {}", requestId, ex);
        }
    }

    private PaymentRefund upsertRefundRecord(Payment payment, YooKassaClient.RefundResponse response) {
        if (payment == null || response == null || response.id == null) {
            return null;
        }
        PaymentRefund refund = paymentRefundRepository.findByRefundId(response.id)
                .orElseGet(PaymentRefund::new);
        refund.setPaymentId(payment.getId());
        refund.setRefundId(response.id);
        refund.setRefundStatus(normalizeRefundStatus(response.status));
        refund.setRefundAmount(toMoney(response.amount));
        refund.setRefundDate(resolveRefundDate(response));
        return paymentRefundRepository.save(refund);
    }

    private void persistSavedPaymentMethod(Order order, YooKassaClient.CreatePaymentResponse response) {
        if (savedPaymentMethodRepository == null || order == null || response == null) {
            return;
        }
        YooKassaClient.PaymentMethod method = response.paymentMethod;
        if (method == null || method.id == null || !Boolean.TRUE.equals(method.saved)) {
            return;
        }
        if (order.getCustomerId() == null) {
            return;
        }
        SavedPaymentMethod savedMethod = savedPaymentMethodRepository
                .findByProviderPaymentMethodId(method.id)
                .orElseGet(SavedPaymentMethod::new);
        savedMethod.setCustomerId(order.getCustomerId());
        savedMethod.setProviderPaymentMethodId(method.id);
        applyPaymentMethodDetails(savedMethod, method);
        savedPaymentMethodRepository.save(savedMethod);
    }

    private void applyPaymentMethodDetails(SavedPaymentMethod target, YooKassaClient.PaymentMethod method) {
        if (target == null || method == null) {
            return;
        }
        target.setMethodType(normalizeValue(method.type));
        target.setMethodStatus(normalizeValue(method.status));
        target.setTitle(method.title);
        if (method.card != null) {
            target.setCardLast4(method.card.last4);
            target.setCardFirst6(method.card.first6);
            target.setCardType(normalizeValue(method.card.cardType));
            target.setCardExpiryMonth(method.card.expiryMonth);
            target.setCardExpiryYear(method.card.expiryYear);
            target.setCardIssuer(method.card.issuerName);
        }
    }

    private void assertRefundMetadataMatches(YooKassaClient.RefundResponse response, Order order) {
        if (response == null || response.metadata == null || order == null) {
            return;
        }
        if (response.metadata.orderId != null && !response.metadata.orderId.equals(order.getId().toString())) {
            throw new IllegalStateException("YooKassa refund metadata mismatch (order_id)");
        }
        if (response.metadata.publicToken != null
                && order.getPublicToken() != null
                && !response.metadata.publicToken.equals(order.getPublicToken())) {
            throw new IllegalStateException("YooKassa refund metadata mismatch (public_token)");
        }
    }

    private Money toMoney(YooKassaClient.Amount amount) {
        if (amount == null || amount.value == null || amount.currency == null) {
            return null;
        }
        BigDecimal value;
        try {
            value = new BigDecimal(amount.value).setScale(2, RoundingMode.HALF_UP);
        } catch (NumberFormatException ex) {
            return null;
        }
        long minor = value.movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact();
        return Money.of(minor, amount.currency);
    }

    private OffsetDateTime resolveRefundDate(YooKassaClient.RefundResponse response) {
        if (response != null && response.createdAt != null) {
            return response.createdAt;
        }
        return OffsetDateTime.now();
    }

    private boolean isRefundSucceeded(String status) {
        return status != null && "succeeded".equalsIgnoreCase(status);
    }

    private boolean isFullRefund(Payment payment, long totalRefunded) {
        if (payment == null || payment.getAmount() == null) {
            return false;
        }
        return totalRefunded == payment.getAmount().getAmount();
    }

    private String normalizeRefundStatus(String status) {
        return status == null ? null : status.trim().toUpperCase();
    }

    private boolean isCancelledStatus(String status) {
        return status != null && "CANCELLED".equalsIgnoreCase(status);
    }

    private String normalizeCurrency(String currency) {
        return currency == null ? "" : currency.trim().toUpperCase();
    }

    private String normalizeValue(String value) {
        return value == null ? null : value.trim().toUpperCase();
    }
}
