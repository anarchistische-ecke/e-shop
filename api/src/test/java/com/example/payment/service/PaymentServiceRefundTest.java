package com.example.payment.service;

import com.example.common.domain.Money;
import com.example.customer.repository.CustomerRepository;
import com.example.order.domain.Order;
import com.example.order.domain.OrderItem;
import com.example.order.repository.OrderRepository;
import com.example.order.service.OrderService;
import com.example.payment.domain.Payment;
import com.example.payment.domain.PaymentRefund;
import com.example.payment.domain.PaymentRefundItem;
import com.example.payment.domain.PaymentStatus;
import com.example.payment.repository.PaymentRefundItemRepository;
import com.example.payment.repository.PaymentRefundRepository;
import com.example.payment.repository.PaymentRepository;
import com.example.payment.repository.SavedPaymentMethodRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceRefundTest {
    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private PaymentRefundRepository paymentRefundRepository;
    @Mock
    private PaymentRefundItemRepository paymentRefundItemRepository;
    @Mock
    private SavedPaymentMethodRepository savedPaymentMethodRepository;
    @Mock
    private OrderRepository orderRepository;
    @Mock
    private YooKassaClient yooKassaClient;

    private PaymentService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        ObjectProvider<PaymentRefundItemRepository> refundItemProvider = org.mockito.Mockito.mock(ObjectProvider.class);
        ObjectProvider<CustomerRepository> customerProvider = org.mockito.Mockito.mock(ObjectProvider.class);
        ObjectProvider<YooKassaClient> yooKassaProvider = org.mockito.Mockito.mock(ObjectProvider.class);
        ObjectProvider<OrderService> orderServiceProvider = org.mockito.Mockito.mock(ObjectProvider.class);
        ObjectProvider<FiscalConfigurationProvider> fiscalProvider = org.mockito.Mockito.mock(ObjectProvider.class);
        ObjectProvider<PromoCodeRedemptionRecorder> promoRecorderProvider = org.mockito.Mockito.mock(ObjectProvider.class);
        when(refundItemProvider.getIfAvailable()).thenReturn(paymentRefundItemRepository);
        when(customerProvider.getIfAvailable()).thenReturn(null);
        when(yooKassaProvider.getIfAvailable()).thenReturn(yooKassaClient);
        when(orderServiceProvider.getIfAvailable()).thenReturn(null);
        when(fiscalProvider.getIfAvailable()).thenReturn(null);
        when(promoRecorderProvider.getIfAvailable()).thenReturn(null);

        service = new PaymentService(
                paymentRepository,
                paymentRefundRepository,
                refundItemProvider,
                savedPaymentMethodRepository,
                orderRepository,
                customerProvider,
                yooKassaProvider,
                orderServiceProvider,
                fiscalProvider,
                promoRecorderProvider
        );
    }

    @Test
    void partialRefundCreatesItemReceiptAndRefundRows() {
        UUID orderId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        Payment payment = completedPayment(orderId, paymentId, 420000);
        Order order = order(orderId, paymentId, "PAID", item(itemId, 2, 210000));
        YooKassaClient.RefundResponse response = refundResponse(order, "refund-1", "2100.00");

        when(paymentRepository.findTopByOrderIdOrderByPaymentDateDesc(orderId)).thenReturn(Optional.of(payment));
        when(paymentRefundRepository.existsByPaymentIdAndRefundStatus(paymentId, "PENDING")).thenReturn(false);
        when(orderRepository.findWithItemsById(orderId)).thenReturn(Optional.of(order));
        when(paymentRefundRepository.sumAmountByPaymentIdAndStatus(paymentId, "SUCCEEDED")).thenReturn(0L);
        when(paymentRefundRepository.countByPaymentId(paymentId)).thenReturn(0L);
        when(yooKassaClient.createRefund(any(YooKassaClient.CreateRefundRequest.class), anyString())).thenReturn(response);
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(paymentRefundRepository.findByRefundId("refund-1")).thenReturn(Optional.empty());
        when(paymentRefundRepository.save(any(PaymentRefund.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(paymentRefundItemRepository.findByRefundIdAndOrderItemId("refund-1", itemId)).thenReturn(Optional.empty());
        when(paymentRefundItemRepository.save(any(PaymentRefundItem.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Payment updated = service.refundYooKassaPayment(
                orderId,
                List.of(new PaymentService.RefundLineRequest(itemId, 1, null))
        );

        ArgumentCaptor<YooKassaClient.CreateRefundRequest> requestCaptor =
                ArgumentCaptor.forClass(YooKassaClient.CreateRefundRequest.class);
        verify(yooKassaClient).createRefund(requestCaptor.capture(), org.mockito.ArgumentMatchers.eq("refund-pay-1-1-210000"));
        YooKassaClient.CreateRefundRequest request = requestCaptor.getValue();
        assertThat(request.paymentId).isEqualTo("pay-1");
        assertThat(request.amount.value).isEqualTo("2100.00");
        assertThat(request.receipt.items).hasSize(1);
        assertThat(request.receipt.items.getFirst().paymentMode).isEqualTo("full_prepayment");
        assertThat(request.receipt.items.getFirst().paymentSubject).isEqualTo("commodity");
        assertThat(request.receipt.items.getFirst().amount.value).isEqualTo("2100.00");
        assertThat(updated.getRefundStatus()).isEqualTo("SUCCEEDED");

        ArgumentCaptor<PaymentRefundItem> itemCaptor = ArgumentCaptor.forClass(PaymentRefundItem.class);
        verify(paymentRefundItemRepository).save(itemCaptor.capture());
        assertThat(itemCaptor.getValue().getOrderItemId()).isEqualTo(itemId);
        assertThat(itemCaptor.getValue().getQuantity()).isEqualTo(1);
        assertThat(itemCaptor.getValue().getRefundAmount().getAmount()).isEqualTo(210000);
    }

    @Test
    void fullRefundMarksPaymentAndOrderRefundedWhenSucceeded() {
        UUID orderId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        Payment payment = completedPayment(orderId, paymentId, 420000);
        Order order = order(orderId, paymentId, "PAID", item(itemId, 2, 210000));
        YooKassaClient.RefundResponse response = refundResponse(order, "refund-full", "4200.00");

        when(paymentRepository.findTopByOrderIdOrderByPaymentDateDesc(orderId)).thenReturn(Optional.of(payment));
        when(paymentRefundRepository.existsByPaymentIdAndRefundStatus(paymentId, "PENDING")).thenReturn(false);
        when(orderRepository.findWithItemsById(orderId)).thenReturn(Optional.of(order));
        when(paymentRefundRepository.sumAmountByPaymentIdAndStatus(paymentId, "SUCCEEDED")).thenReturn(0L, 420000L);
        when(paymentRefundRepository.countByPaymentId(paymentId)).thenReturn(0L);
        when(yooKassaClient.createRefund(any(YooKassaClient.CreateRefundRequest.class), anyString())).thenReturn(response);
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(paymentRefundRepository.findByRefundId("refund-full")).thenReturn(Optional.empty());
        when(paymentRefundRepository.save(any(PaymentRefund.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(paymentRefundItemRepository.findByRefundIdAndOrderItemId("refund-full", itemId)).thenReturn(Optional.empty());
        when(paymentRefundItemRepository.save(any(PaymentRefundItem.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Payment updated = service.refundYooKassaPayment(orderId, List.of());

        assertThat(updated.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(order.getStatus()).isEqualTo("REFUNDED");
        verify(orderRepository).save(order);
    }

    @Test
    void pendingRefundBlocksDuplicateRefund() {
        UUID orderId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        Payment payment = completedPayment(orderId, paymentId, 420000);

        when(paymentRepository.findTopByOrderIdOrderByPaymentDateDesc(orderId)).thenReturn(Optional.of(payment));
        when(paymentRefundRepository.existsByPaymentIdAndRefundStatus(paymentId, "PENDING")).thenReturn(true);

        assertThatThrownBy(() -> service.refundYooKassaPayment(orderId, List.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already pending");
        verify(yooKassaClient, never()).createRefund(any(), anyString());
    }

    @Test
    void overRefundFailsBeforeCallingProvider() {
        UUID orderId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        Payment payment = completedPayment(orderId, paymentId, 420000);
        Order order = order(orderId, paymentId, "PAID", item(itemId, 1, 420000));

        when(paymentRepository.findTopByOrderIdOrderByPaymentDateDesc(orderId)).thenReturn(Optional.of(payment));
        when(paymentRefundRepository.existsByPaymentIdAndRefundStatus(paymentId, "PENDING")).thenReturn(false);
        when(orderRepository.findWithItemsById(orderId)).thenReturn(Optional.of(order));
        when(paymentRefundRepository.sumAmountByPaymentIdAndStatus(paymentId, "SUCCEEDED")).thenReturn(0L);

        assertThatThrownBy(() -> service.refundYooKassaPayment(
                orderId,
                List.of(new PaymentService.RefundLineRequest(itemId, 1, 500000L))
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exceeds remaining amount");
        verify(yooKassaClient, never()).createRefund(any(), anyString());
    }

    private Payment completedPayment(UUID orderId, UUID paymentId, long amount) {
        Payment payment = new Payment(orderId, Money.of(amount, "RUB"), "YOOKASSA", PaymentStatus.COMPLETED);
        payment.setId(paymentId);
        payment.setProviderPaymentId("pay-1");
        return payment;
    }

    private Order order(UUID orderId, UUID paymentId, String status, OrderItem item) {
        Order order = new Order(UUID.randomUUID(), status, Money.of(420000, "RUB"));
        order.setId(orderId);
        order.setPaymentId(paymentId);
        order.setPublicToken("public-token");
        order.setReceiptEmail("buyer@example.test");
        order.setContactPhone("+7 999 000-00-00");
        order.addItem(item);
        return order;
    }

    private OrderItem item(UUID itemId, int quantity, long unitAmount) {
        OrderItem item = new OrderItem(UUID.randomUUID(), quantity, Money.of(unitAmount, "RUB"));
        item.setId(itemId);
        item.setProductName("Комплект Linen");
        item.setVariantName("Евро");
        return item;
    }

    private YooKassaClient.RefundResponse refundResponse(Order order, String refundId, String amount) {
        YooKassaClient.RefundResponse response = new YooKassaClient.RefundResponse();
        response.id = refundId;
        response.status = "succeeded";
        response.paymentId = "pay-1";
        response.amount = YooKassaClient.Amount.of(amount, "RUB");
        response.metadata = YooKassaClient.Metadata.of(order.getId().toString(), order.getPublicToken());
        return response;
    }
}
