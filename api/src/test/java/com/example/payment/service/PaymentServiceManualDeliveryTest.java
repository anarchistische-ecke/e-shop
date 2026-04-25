package com.example.payment.service;

import com.example.common.domain.Money;
import com.example.order.domain.Order;
import com.example.order.domain.OrderItem;
import com.example.order.repository.OrderRepository;
import com.example.order.service.OrderService;
import com.example.payment.domain.Payment;
import com.example.payment.domain.PaymentStatus;
import com.example.payment.repository.PaymentRefundRepository;
import com.example.payment.repository.PaymentRepository;
import com.example.payment.repository.SavedPaymentMethodRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentServiceManualDeliveryTest {

    @Test
    @SuppressWarnings("unchecked")
    void createYooKassaPaymentUsesProductTotalAndNoDeliveryReceiptLineForManualDelivery() {
        PaymentRepository paymentRepository = mock(PaymentRepository.class);
        PaymentRefundRepository paymentRefundRepository = mock(PaymentRefundRepository.class);
        SavedPaymentMethodRepository savedPaymentMethodRepository = mock(SavedPaymentMethodRepository.class);
        OrderRepository orderRepository = mock(OrderRepository.class);
        YooKassaClient yooKassaClient = mock(YooKassaClient.class);
        ObjectProvider<com.example.customer.repository.CustomerRepository> customerRepositoryProvider = mock(ObjectProvider.class);
        ObjectProvider<YooKassaClient> yooKassaClientProvider = mock(ObjectProvider.class);
        ObjectProvider<OrderService> orderServiceProvider = mock(ObjectProvider.class);
        when(customerRepositoryProvider.getIfAvailable()).thenReturn(null);
        when(yooKassaClientProvider.getIfAvailable()).thenReturn(yooKassaClient);
        when(orderServiceProvider.getIfAvailable()).thenReturn(null);

        PaymentService service = new PaymentService(
                paymentRepository,
                paymentRefundRepository,
                savedPaymentMethodRepository,
                orderRepository,
                customerRepositoryProvider,
                yooKassaClientProvider,
                orderServiceProvider
        );

        UUID orderId = UUID.randomUUID();
        Order order = new Order(UUID.randomUUID(), "PENDING", Money.of(420000, "RUB"));
        order.setId(orderId);
        order.setPublicToken("public-token");
        order.setReceiptEmail("buyer@example.test");
        order.setContactPhone("+7 (999) 000-00-00");
        OrderItem item = new OrderItem(UUID.randomUUID(), 1, Money.of(420000, "RUB"));
        item.setProductName("Сатиновый комплект Sand");
        item.setVariantName("200x220");
        order.addItem(item);

        when(orderRepository.findWithItemsById(orderId)).thenReturn(Optional.of(order));
        when(paymentRepository.findTopByOrderIdOrderByPaymentDateDesc(orderId)).thenReturn(Optional.empty());
        YooKassaClient.CreatePaymentResponse response = new YooKassaClient.CreatePaymentResponse();
        response.id = "pay-test-1";
        response.status = "pending";
        response.confirmation = YooKassaClient.Confirmation.embedded();
        when(yooKassaClient.createPayment(any(YooKassaClient.CreatePaymentRequest.class), eq("order-key")))
                .thenReturn(response);
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> {
            Payment payment = invocation.getArgument(0);
            payment.setId(UUID.randomUUID());
            return payment;
        });

        Payment payment = service.createYooKassaPayment(
                orderId,
                "buyer@example.test",
                "https://example.test/order/public-token",
                "order-key",
                false,
                null,
                "EMBEDDED"
        );

        org.mockito.ArgumentCaptor<YooKassaClient.CreatePaymentRequest> captor =
                org.mockito.ArgumentCaptor.forClass(YooKassaClient.CreatePaymentRequest.class);
        verify(yooKassaClient).createPayment(captor.capture(), eq("order-key"));
        YooKassaClient.CreatePaymentRequest request = captor.getValue();

        assertEquals("4200.00", request.amount.value);
        assertEquals("RUB", request.amount.currency);
        assertEquals(1, request.receipt.items.size());
        assertEquals("Сатиновый комплект Sand (200x220)", request.receipt.items.getFirst().description);
        assertEquals("+79990000000", request.receipt.customer.phone);
        assertEquals(420000, payment.getAmount().getAmount());
        assertNull(order.getDeliveryAmount());
    }
}
