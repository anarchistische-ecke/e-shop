package com.example.api.order;

import com.example.api.admincms.DirectusAdminService;
import com.example.api.notification.EmailService;
import com.example.api.notification.NotificationOrchestrator;
import com.example.common.domain.Money;
import com.example.customer.domain.Customer;
import com.example.customer.service.CustomerService;
import com.example.order.domain.Order;
import com.example.order.service.OrderService;
import com.example.payment.domain.Payment;
import com.example.payment.domain.PaymentStatus;
import com.example.payment.service.PaymentService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderControllerCheckoutTest {

    @Test
    void checkoutAcceptsIdempotencyKeyFromBodyWhenHeaderIsAbsent() {
        OrderService orderService = mock(OrderService.class);
        CustomerService customerService = mock(CustomerService.class);
        PaymentService paymentService = mock(PaymentService.class);
        OrderController controller = new OrderController(
                orderService,
                customerService,
                paymentService,
                mock(EmailService.class),
                mock(DirectusAdminService.class),
                mock(NotificationOrchestrator.class)
        );
        UUID cartId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        Customer customer = new Customer();
        customer.setId(customerId);
        Order order = new Order(customerId, "PENDING", Money.of(150000L, "RUB"));
        order.setId(orderId);
        order.setPublicToken("public-token");
        Payment payment = new Payment(orderId, Money.of(150000L, "RUB"), "YOOKASSA", PaymentStatus.PENDING);
        payment.setId(UUID.randomUUID());
        payment.setConfirmationUrl("https://yookassa.test/pay");

        when(customerService.findOrCreateByEmail("customer@example.test", null, null)).thenReturn(customer);
        when(customerService.applyCheckoutContact(customer, "Customer", "+79990000000")).thenReturn(customer);
        when(orderService.acquireCheckoutAttempt(eq("body-key-1"), anyString()))
                .thenReturn(OrderService.CheckoutAttemptState.reserved());
        when(orderService.createOrderFromCart(
                eq(cartId),
                eq(customerId),
                eq("customer@example.test"),
                isNull(),
                any(OrderService.ContactSpec.class)
        )).thenReturn(order);
        when(paymentService.createYooKassaPayment(
                eq(orderId),
                eq("customer@example.test"),
                eq("https://yug-postel.ru/order/public-token"),
                eq("order-" + orderId),
                eq(false),
                eq(customerId.toString()),
                eq("REDIRECT")
        )).thenReturn(payment);

        ResponseEntity<OrderController.OrderCheckoutResponse> response = controller.checkout(
                new OrderController.CheckoutRequest(
                        cartId,
                        "customer@example.test",
                        "Customer",
                        "+79990000000",
                        "Address",
                        "https://yug-postel.ru/order/{token}",
                        "https://yug-postel.ru/order/{token}",
                        "REDIRECT",
                        false,
                        "body-key-1"
                ),
                null,
                null
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().payment().confirmationUrl()).isEqualTo("https://yookassa.test/pay");
        verify(orderService).completeCheckoutAttempt(eq("body-key-1"), anyString(), eq(orderId));
    }
}
