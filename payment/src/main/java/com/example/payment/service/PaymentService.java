package com.example.payment.service;

import com.example.common.domain.Money;
import com.example.customer.domain.Customer;
import com.example.customer.repository.CustomerRepository;
import com.example.order.domain.Order;
import com.example.order.domain.OrderItem;
import com.example.order.repository.OrderRepository;
import com.example.order.service.OrderService;
import com.example.payment.domain.Payment;
import com.example.payment.domain.PaymentRefund;
import com.example.payment.domain.PaymentRefundItem;
import com.example.payment.domain.PaymentStatus;
import com.example.payment.domain.SavedPaymentMethod;
import com.example.payment.repository.PaymentRefundItemRepository;
import com.example.payment.repository.PaymentRefundRepository;
import com.example.payment.repository.PaymentRepository;
import com.example.payment.repository.SavedPaymentMethodRepository;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class PaymentService {
    private final PaymentRepository paymentRepository;
    private final PaymentRefundRepository paymentRefundRepository;
    private final PaymentRefundItemRepository paymentRefundItemRepository;
    private final SavedPaymentMethodRepository savedPaymentMethodRepository;
    private final OrderRepository orderRepository;
    private final CustomerRepository customerRepository;
    private final YooKassaClient yooKassaClient;
    private final OrderService orderService;
    private final FiscalConfigurationProvider fiscalConfigurationProvider;
    private final PromoCodeRedemptionRecorder promoCodeRedemptionRecorder;

    @Autowired
    public PaymentService(PaymentRepository paymentRepository,
                          PaymentRefundRepository paymentRefundRepository,
                          ObjectProvider<PaymentRefundItemRepository> paymentRefundItemRepositoryProvider,
                          SavedPaymentMethodRepository savedPaymentMethodRepository,
                          OrderRepository orderRepository,
                          ObjectProvider<CustomerRepository> customerRepositoryProvider,
                          ObjectProvider<YooKassaClient> yooKassaClientProvider,
                          ObjectProvider<OrderService> orderServiceProvider,
                          ObjectProvider<FiscalConfigurationProvider> fiscalConfigurationProviderProvider,
                          ObjectProvider<PromoCodeRedemptionRecorder> promoCodeRedemptionRecorderProvider) {
        this.paymentRepository = paymentRepository;
        this.paymentRefundRepository = paymentRefundRepository;
        this.paymentRefundItemRepository = paymentRefundItemRepositoryProvider.getIfAvailable();
        this.savedPaymentMethodRepository = savedPaymentMethodRepository;
        this.orderRepository = orderRepository;
        this.customerRepository = customerRepositoryProvider.getIfAvailable();
        this.yooKassaClient = yooKassaClientProvider.getIfAvailable();
        this.orderService = orderServiceProvider.getIfAvailable();
        this.fiscalConfigurationProvider = fiscalConfigurationProviderProvider.getIfAvailable();
        this.promoCodeRedemptionRecorder = promoCodeRedemptionRecorderProvider.getIfAvailable();
    }

    public Payment createYooKassaPayment(UUID orderId,
                                         String receiptEmail,
                                         String returnUrl,
                                         String idempotencyKey,
                                         Boolean savePaymentMethod,
                                         String merchantCustomerId,
                                         String confirmationMode) {
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
            if (latest.getStatus() == PaymentStatus.PENDING && isPendingPaymentReusable(latest)) {
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
        request.confirmation = createConfirmation(returnUrl, confirmationMode);
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
        applyConfirmationDetails(payment, response.confirmation, request.confirmation);
        applyReceiptDetails(payment, response);
        payment.setStatus(mapYooKassaStatus(response.status));
        payment = paymentRepository.save(payment);

        syncOrderWithPayment(order, payment, payment.getStatus());
        persistSavedPaymentMethod(order, response);
        return payment;
    }

    public Payment refreshYooKassaPayment(String providerPaymentId) {
        return refreshYooKassaPaymentWithResult(providerPaymentId).payment();
    }

    public Payment findByProviderPaymentId(String providerPaymentId) {
        if (!StringUtils.hasText(providerPaymentId)) {
            return null;
        }
        return paymentRepository.findByProviderPaymentId(providerPaymentId).orElse(null);
    }

    public PaymentSummary getPaymentSummary(UUID orderId) {
        if (orderId == null) {
            return null;
        }
        Payment payment = paymentRepository.findTopByOrderIdOrderByPaymentDateDesc(orderId).orElse(null);
        if (payment == null) {
            return null;
        }
        long refunded = Math.max(0L, valueOrZero(
                paymentRefundRepository.sumAmountByPaymentIdAndStatus(payment.getId(), "SUCCEEDED")
        ));
        String currency = payment.getAmount() != null ? payment.getAmount().getCurrency() : "RUB";
        long paidAmount = payment.getAmount() != null ? payment.getAmount().getAmount() : 0L;
        long refundable = Math.max(0L, paidAmount - refunded);
        Map<String, List<PaymentRefundItem>> itemsByRefundId = paymentRefundItemRepository != null
                ? paymentRefundItemRepository.findByPaymentIdOrderByCreatedAtDesc(payment.getId()).stream()
                    .filter(item -> StringUtils.hasText(item.getRefundId()))
                    .collect(Collectors.groupingBy(PaymentRefundItem::getRefundId))
                : Map.of();
        List<PaymentSummary.RefundSummary> refunds = paymentRefundRepository
                .findByPaymentIdOrderByCreatedAtDesc(payment.getId()).stream()
                .map(refund -> new PaymentSummary.RefundSummary(
                        refund.getId(),
                        refund.getRefundId(),
                        refund.getRefundStatus(),
                        refund.getRefundAmount(),
                        refund.getRefundDate(),
                        itemsByRefundId.getOrDefault(refund.getRefundId(), List.of()).stream()
                                .map(item -> new PaymentSummary.RefundItemSummary(
                                        item.getOrderItemId(),
                                        item.getQuantity(),
                                        item.getRefundAmount(),
                                        item.getRefundStatus()
                                ))
                                .toList()
                ))
                .toList();
        return new PaymentSummary(
                payment.getId(),
                payment.getProviderPaymentId(),
                payment.getMethod(),
                payment.getStatus(),
                payment.getAmount(),
                payment.getReceiptRegistration(),
                payment.getReceiptUrl(),
                Money.of(refunded, currency),
                Money.of(refundable, currency),
                refunds
        );
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
            applyConfirmationDetails(payment, response.confirmation, null);
            applyReceiptDetails(payment, response);
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
        applyReceiptDetails(payment, response);
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

    public record RefundLineRequest(UUID orderItemId, Integer quantity, Long amount) {}

    private record PreparedRefundLine(OrderItem item, int quantity, Money amount) {}

    public Payment refundYooKassaPayment(UUID orderId) {
        return refundYooKassaPayment(orderId, List.of());
    }

    public Payment refundYooKassaPayment(UUID orderId, List<RefundLineRequest> refundLines) {
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
        if (paymentRefundRepository.existsByPaymentIdAndRefundStatus(payment.getId(), "PENDING")) {
            throw new IllegalStateException("A refund is already pending for this payment");
        }
        Order order = orderRepository.findWithItemsById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found: " + orderId));
        long totalRefunded = Math.max(0L, valueOrZero(
                paymentRefundRepository.sumAmountByPaymentIdAndStatus(payment.getId(), "SUCCEEDED")
        ));
        long remainingAmount = Math.max(0L, payment.getAmount().getAmount() - totalRefunded);
        if (remainingAmount <= 0L) {
            throw new IllegalStateException("Payment has no refundable amount remaining");
        }
        boolean fullRemainingRequest = refundLines == null || refundLines.isEmpty();
        List<PreparedRefundLine> preparedLines = fullRemainingRequest
                ? prepareRemainingRefundLines(payment, order)
                : prepareRequestedRefundLines(payment, order, refundLines);
        long requestedAmount = preparedLines.stream()
                .map(PreparedRefundLine::amount)
                .filter(Objects::nonNull)
                .mapToLong(Money::getAmount)
                .sum();
        boolean useOriginalReceiptForFullRefund = fullRemainingRequest
                && totalRefunded == 0L
                && requestedAmount < remainingAmount;
        if (useOriginalReceiptForFullRefund) {
            requestedAmount = remainingAmount;
        }
        if (requestedAmount <= 0L) {
            throw new IllegalArgumentException("Refund amount must be greater than zero");
        }
        if (requestedAmount > remainingAmount) {
            throw new IllegalArgumentException("Refund amount exceeds remaining refundable payment amount");
        }

        YooKassaClient.CreateRefundRequest request = new YooKassaClient.CreateRefundRequest();
        request.paymentId = payment.getProviderPaymentId();
        request.amount = YooKassaClient.Amount.of(formatAmount(Money.of(requestedAmount, payment.getAmount().getCurrency())), payment.getAmount().getCurrency());
        request.metadata = YooKassaClient.Metadata.of(orderId.toString(), order.getPublicToken());
        if (order.getReceiptEmail() != null && !order.getReceiptEmail().isBlank()) {
            request.receipt = useOriginalReceiptForFullRefund
                    ? buildReceipt(order, order.getReceiptEmail())
                    : buildRefundReceipt(order, order.getReceiptEmail(), preparedLines);
        }

        YooKassaClient.RefundResponse response = yooKassaClient.createRefund(
                request,
                buildRefundIdempotencyKey(payment, requestedAmount)
        );
        if (response == null || response.id == null) {
            throw new PaymentProcessingException("Failed to create YooKassa refund");
        }
        assertRefundMetadataMatches(response, order);
        Payment updated = applyRefundResponse(payment, order, response);
        saveRefundItems(updated, response, preparedLines);
        return updated;
    }

    private Payment applyRefundResponse(Payment payment, Order order, YooKassaClient.RefundResponse response) {
        payment.setRefundId(response.id);
        payment.setRefundStatus(normalizeRefundStatus(response.status));
        payment.setRefundAmount(toMoney(response.amount));
        payment.setRefundDate(resolveRefundDate(response));
        payment = paymentRepository.save(payment);

        upsertRefundRecord(payment, response);
        if (paymentRefundItemRepository != null) {
            paymentRefundItemRepository.updateRefundStatusByRefundId(response.id, normalizeRefundStatus(response.status));
        }

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
        }
        return payment;
    }

    private List<PreparedRefundLine> prepareRequestedRefundLines(Payment payment,
                                                                 Order order,
                                                                 List<RefundLineRequest> refundLines) {
        if (refundLines == null || refundLines.isEmpty()) {
            return List.of();
        }
        Map<UUID, OrderItem> itemsById = order.getItems().stream()
                .filter(item -> item.getId() != null)
                .collect(Collectors.toMap(OrderItem::getId, item -> item));
        List<PreparedRefundLine> prepared = new ArrayList<>();
        List<UUID> seenOrderItems = new ArrayList<>();
        for (RefundLineRequest line : refundLines) {
            if (line == null || line.orderItemId() == null) {
                throw new IllegalArgumentException("Refund line orderItemId is required");
            }
            if (seenOrderItems.contains(line.orderItemId())) {
                throw new IllegalArgumentException("Duplicate refund line for order item: " + line.orderItemId());
            }
            seenOrderItems.add(line.orderItemId());
            OrderItem item = itemsById.get(line.orderItemId());
            if (item == null) {
                throw new IllegalArgumentException("Order item not found for refund: " + line.orderItemId());
            }
            int quantity = line.quantity() != null ? line.quantity() : 1;
            if (quantity <= 0) {
                throw new IllegalArgumentException("Refund line quantity must be greater than zero");
            }
            long refundedQuantity = refundedItemQuantity(payment.getId(), item.getId());
            int remainingQuantity = Math.max(0, item.getQuantity() - (int) refundedQuantity);
            if (quantity > remainingQuantity) {
                throw new IllegalArgumentException("Refund quantity exceeds remaining quantity for order item: " + item.getId());
            }
            long itemRemainingAmount = Math.max(0L, item.getPayableTotalAmount() - refundedItemAmount(payment.getId(), item.getId()));
            long requestedAmount = line.amount() != null
                    ? line.amount()
                    : proratedRefundAmount(item, quantity, remainingQuantity, itemRemainingAmount);
            if (requestedAmount <= 0L) {
                throw new IllegalArgumentException("Refund line amount must be greater than zero");
            }
            if (requestedAmount > itemRemainingAmount) {
                throw new IllegalArgumentException("Refund line amount exceeds remaining amount for order item: " + item.getId());
            }
            String currency = item.getPayableAmount() != null
                    ? item.getPayableAmount().getCurrency()
                    : item.getUnitPrice().getCurrency();
            prepared.add(new PreparedRefundLine(item, quantity, Money.of(requestedAmount, currency)));
        }
        return prepared;
    }

    private List<PreparedRefundLine> prepareRemainingRefundLines(Payment payment, Order order) {
        if (order.getItems() == null || order.getItems().isEmpty()) {
            return List.of();
        }
        return order.getItems().stream()
                .filter(item -> item.getId() != null && item.getQuantity() > 0)
                .map(item -> {
                    long refundedQuantity = refundedItemQuantity(payment.getId(), item.getId());
                    int remainingQuantity = Math.max(0, item.getQuantity() - (int) refundedQuantity);
                    long remainingAmount = Math.max(0L, item.getPayableTotalAmount() - refundedItemAmount(payment.getId(), item.getId()));
                    if (remainingQuantity <= 0 || remainingAmount <= 0L) {
                        return null;
                    }
                    String currency = item.getPayableAmount() != null
                            ? item.getPayableAmount().getCurrency()
                            : item.getUnitPrice().getCurrency();
                    return new PreparedRefundLine(item, remainingQuantity, Money.of(remainingAmount, currency));
                })
                .filter(Objects::nonNull)
                .toList();
    }

    private long proratedRefundAmount(OrderItem item, int quantity, int remainingQuantity, long itemRemainingAmount) {
        if (quantity == remainingQuantity) {
            return itemRemainingAmount;
        }
        long payableTotal = item.getPayableTotalAmount();
        long prorated = Math.round((double) payableTotal * quantity / item.getQuantity());
        return Math.min(Math.max(0L, prorated), itemRemainingAmount);
    }

    private long refundedItemQuantity(UUID paymentId, UUID orderItemId) {
        if (paymentRefundItemRepository == null || paymentId == null || orderItemId == null) {
            return 0L;
        }
        return valueOrZero(paymentRefundItemRepository.sumQuantityByPaymentIdAndOrderItemIdAndStatus(
                paymentId,
                orderItemId,
                "SUCCEEDED"
        ));
    }

    private long refundedItemAmount(UUID paymentId, UUID orderItemId) {
        if (paymentRefundItemRepository == null || paymentId == null || orderItemId == null) {
            return 0L;
        }
        return valueOrZero(paymentRefundItemRepository.sumAmountByPaymentIdAndOrderItemIdAndStatus(
                paymentId,
                orderItemId,
                "SUCCEEDED"
        ));
    }

    private void saveRefundItems(Payment payment,
                                 YooKassaClient.RefundResponse response,
                                 List<PreparedRefundLine> preparedLines) {
        if (paymentRefundItemRepository == null || payment == null || response == null || !StringUtils.hasText(response.id)) {
            return;
        }
        if (preparedLines == null || preparedLines.isEmpty()) {
            return;
        }
        String status = normalizeRefundStatus(response.status);
        preparedLines.forEach(line -> {
            PaymentRefundItem item = paymentRefundItemRepository
                    .findByRefundIdAndOrderItemId(response.id, line.item().getId())
                    .orElseGet(PaymentRefundItem::new);
            item.setPaymentId(payment.getId());
            item.setRefundId(response.id);
            item.setOrderItemId(line.item().getId());
            item.setQuantity(line.quantity());
            item.setRefundAmount(line.amount());
            item.setRefundStatus(status);
            paymentRefundItemRepository.save(item);
        });
    }

    private YooKassaClient.Receipt buildRefundReceipt(Order order,
                                                      String email,
                                                      List<PreparedRefundLine> refundLines) {
        if (email == null || email.isBlank() || refundLines == null || refundLines.isEmpty()) {
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
        refundLines.forEach(line -> receiptItems.addAll(buildReceiptItemsForRefundLine(line)));
        receipt.items = receiptItems;
        receipt.taxSystemCode = resolveTaxSystemCode();
        return receipt;
    }

    private List<YooKassaClient.ReceiptItem> buildReceiptItemsForRefundLine(PreparedRefundLine line) {
        if (line == null || line.item() == null || line.quantity() <= 0 || line.amount() == null || line.amount().getAmount() <= 0L) {
            return List.of();
        }
        long total = line.amount().getAmount();
        int quantity = line.quantity();
        long unitAmount = total / quantity;
        int remainder = (int) (total % quantity);
        if (unitAmount <= 0L) {
            throw new IllegalArgumentException("Refund line amount is too small for the selected quantity");
        }
        if (remainder == 0) {
            return List.of(buildReceiptItem(line.item(), quantity, Money.of(unitAmount, line.amount().getCurrency()), null));
        }
        List<YooKassaClient.ReceiptItem> items = new ArrayList<>();
        items.add(buildReceiptItem(line.item(), remainder, Money.of(unitAmount + 1, line.amount().getCurrency()), " / возврат 1"));
        int remainingQuantity = quantity - remainder;
        if (remainingQuantity > 0) {
            items.add(buildReceiptItem(line.item(), remainingQuantity, Money.of(unitAmount, line.amount().getCurrency()), " / возврат 2"));
        }
        return items;
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
        order.getItems().forEach(item -> receiptItems.addAll(buildReceiptItemsForOrderItem(item)));
        if (order.getDeliveryAmount() != null && order.getDeliveryAmount().getAmount() > 0) {
            YooKassaClient.ReceiptItem deliveryItem = new YooKassaClient.ReceiptItem();
            deliveryItem.description = "Доставка";
            deliveryItem.quantity = BigDecimal.ONE;
            deliveryItem.amount = YooKassaClient.Amount.of(formatAmount(order.getDeliveryAmount()), order.getDeliveryAmount().getCurrency());
            deliveryItem.vatCode = resolveVatCode();
            deliveryItem.paymentMode = "full_prepayment";
            deliveryItem.paymentSubject = "service";
            receiptItems.add(deliveryItem);
        }
        receipt.items = receiptItems;
        receipt.taxSystemCode = resolveTaxSystemCode();
        return receipt;
    }

    private List<YooKassaClient.ReceiptItem> buildReceiptItemsForOrderItem(OrderItem item) {
        if (item == null || item.getQuantity() <= 0 || item.getUnitPrice() == null) {
            return List.of();
        }
        long payableTotal = Math.max(0L, item.getPayableTotalAmount());
        if (payableTotal <= 0L) {
            return List.of();
        }
        int quantity = item.getQuantity();
        String currency = item.getUnitPrice().getCurrency();
        long unitAmount = payableTotal / quantity;
        int remainder = (int) (payableTotal % quantity);
        if (remainder == 0) {
            return List.of(buildReceiptItem(item, quantity, Money.of(unitAmount, currency), null));
        }

        List<YooKassaClient.ReceiptItem> items = new ArrayList<>();
        items.add(buildReceiptItem(item, remainder, Money.of(unitAmount + 1, currency), " / часть 1"));
        int remainingQuantity = quantity - remainder;
        if (remainingQuantity > 0 && unitAmount > 0L) {
            items.add(buildReceiptItem(item, remainingQuantity, Money.of(unitAmount, currency), " / часть 2"));
        }
        return items;
    }

    private YooKassaClient.ReceiptItem buildReceiptItem(OrderItem item, int quantity, Money unitPrice, String suffix) {
        YooKassaClient.ReceiptItem receiptItem = new YooKassaClient.ReceiptItem();
        receiptItem.description = buildItemDescription(item) + (suffix != null ? suffix : "");
        receiptItem.quantity = BigDecimal.valueOf(quantity);
        receiptItem.amount = YooKassaClient.Amount.of(formatAmount(unitPrice), unitPrice.getCurrency());
        receiptItem.vatCode = resolveVatCode();
        receiptItem.paymentMode = "full_prepayment";
        receiptItem.paymentSubject = "commodity";
        return receiptItem;
    }

    private int resolveVatCode() {
        FiscalConfigurationProvider.FiscalConfiguration config = fiscalConfigurationProvider != null
                ? fiscalConfigurationProvider.getActiveFiscalConfiguration()
                : null;
        return config != null ? config.vatCode() : yooKassaClient.getVatCode();
    }

    private int resolveTaxSystemCode() {
        FiscalConfigurationProvider.FiscalConfiguration config = fiscalConfigurationProvider != null
                ? fiscalConfigurationProvider.getActiveFiscalConfiguration()
                : null;
        return config != null ? config.taxSystemCode() : yooKassaClient.getTaxSystemCode();
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

    private YooKassaClient.Confirmation createConfirmation(String returnUrl, String confirmationMode) {
        if ("EMBEDDED".equalsIgnoreCase(confirmationMode)) {
            return YooKassaClient.Confirmation.embedded();
        }
        return YooKassaClient.Confirmation.redirect(returnUrl);
    }

    private boolean isPendingPaymentReusable(Payment payment) {
        if (payment == null || payment.getStatus() != PaymentStatus.PENDING) {
            return false;
        }
        return StringUtils.hasText(payment.getConfirmationUrl())
                || StringUtils.hasText(payment.getConfirmationToken())
                || StringUtils.hasText(payment.getProviderPaymentId());
    }

    private void applyConfirmationDetails(Payment payment,
                                          YooKassaClient.Confirmation responseConfirmation,
                                          YooKassaClient.Confirmation requestedConfirmation) {
        if (payment == null) {
            return;
        }
        String confirmationType = null;
        if (responseConfirmation != null && StringUtils.hasText(responseConfirmation.type)) {
            confirmationType = responseConfirmation.type.trim().toUpperCase();
        } else if (requestedConfirmation != null && StringUtils.hasText(requestedConfirmation.type)) {
            confirmationType = requestedConfirmation.type.trim().toUpperCase();
        }
        if (confirmationType != null) {
            payment.setConfirmationType(confirmationType);
        }
        if (responseConfirmation != null) {
            if (responseConfirmation.confirmationUrl != null) {
                payment.setConfirmationUrl(StringUtils.hasText(responseConfirmation.confirmationUrl)
                        ? responseConfirmation.confirmationUrl.trim()
                        : null);
            }
            if (StringUtils.hasText(responseConfirmation.confirmationToken)) {
                payment.setConfirmationToken(responseConfirmation.confirmationToken.trim());
            }
        }
    }

    private void applyReceiptDetails(Payment payment, YooKassaClient.CreatePaymentResponse response) {
        if (payment == null || response == null) {
            return;
        }
        if (StringUtils.hasText(response.receiptRegistration)) {
            payment.setReceiptRegistration(response.receiptRegistration.trim());
        }
        if (StringUtils.hasText(response.receiptUrl)) {
            payment.setReceiptUrl(response.receiptUrl.trim());
        }
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

    private String buildRefundIdempotencyKey(Payment payment, long requestedAmount) {
        long nextIndex = paymentRefundRepository.countByPaymentId(payment.getId()) + 1L;
        return "refund-" + payment.getProviderPaymentId() + "-" + nextIndex + "-" + requestedAmount;
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
        boolean completedNow = status == PaymentStatus.COMPLETED && !isOrderPaid(order);
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
        }
        if (completedNow && promoCodeRedemptionRecorder != null) {
            promoCodeRedemptionRecorder.recordPaidOrder(order);
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
        if (order != null && StringUtils.hasText(order.getContactPhone())) {
            String normalizedOrderPhone = normalizeReceiptPhone(order.getContactPhone());
            if (StringUtils.hasText(normalizedOrderPhone)) {
                return normalizedOrderPhone;
            }
        }
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
        return normalizeReceiptPhone(phone);
    }

    private String normalizeReceiptPhone(String phone) {
        if (!StringUtils.hasText(phone)) {
            return null;
        }
        String trimmed = phone.trim();
        String prefix = trimmed.startsWith("+") ? "+" : "";
        String digits = trimmed.replaceAll("\\D", "");
        String normalized = prefix + digits;
        return normalized.matches("^\\+?\\d{11,15}$") ? normalized : null;
    }

    private void restockOrder(UUID orderId, String reason, String idempotencyPrefix) {
        if (orderService == null || orderId == null) {
            return;
        }
        orderService.restockOrderItems(orderId, reason, idempotencyPrefix);
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
