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
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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
        when(orderService.createOrderFromCartAndCompleteCheckoutAttempt(
                eq("body-key-1"),
                anyString(),
                eq(cartId),
                eq(customerId),
                eq("customer@example.test"),
                isNull(),
                any(OrderService.ContactSpec.class),
                isNull()
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
                        null,
                        "body-key-1"
                ),
                null,
                null
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().payment().confirmationUrl()).isEqualTo("https://yookassa.test/pay");
        verify(orderService, never()).completeCheckoutAttempt(anyString(), anyString(), any());
    }

    @Test
    void checkoutDoesNotReleaseBoundAttemptWhenPaymentCreationFails() {
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

        when(customerService.findOrCreateByEmail("customer@example.test", null, null)).thenReturn(customer);
        when(customerService.applyCheckoutContact(customer, "Customer", "+79990000000")).thenReturn(customer);
        when(orderService.acquireCheckoutAttempt(eq("body-key-1"), anyString()))
                .thenReturn(OrderService.CheckoutAttemptState.reserved());
        when(orderService.createOrderFromCartAndCompleteCheckoutAttempt(
                eq("body-key-1"),
                anyString(),
                eq(cartId),
                eq(customerId),
                eq("customer@example.test"),
                isNull(),
                any(OrderService.ContactSpec.class),
                isNull()
        )).thenReturn(order);
        when(paymentService.createYooKassaPayment(
                eq(orderId),
                eq("customer@example.test"),
                eq("https://yug-postel.ru/order/public-token"),
                eq("order-" + orderId),
                eq(false),
                eq(customerId.toString()),
                eq("REDIRECT")
        )).thenThrow(new IllegalStateException("payment unavailable"));

        assertThatThrownBy(() -> controller.checkout(
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
                        null,
                        "body-key-1"
                ),
                null,
                null
        )).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("payment unavailable");

        verify(orderService, never()).releaseCheckoutAttemptIfUnbound(anyString(), anyString());
    }

    @Test
    void checkoutReplayUsesExistingBoundOrder() {
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
        payment.setConfirmationUrl("https://yookassa.test/pay");

        when(customerService.findOrCreateByEmail("customer@example.test", null, null)).thenReturn(customer);
        when(customerService.applyCheckoutContact(customer, "Customer", "+79990000000")).thenReturn(customer);
        when(orderService.acquireCheckoutAttempt(eq("body-key-1"), anyString()))
                .thenReturn(OrderService.CheckoutAttemptState.completed(orderId));
        when(orderService.findOrderByCheckoutAttempt(eq("body-key-1"), anyString())).thenReturn(order);
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
                        null,
                        "body-key-1"
                ),
                null,
                null
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().order().getId()).isEqualTo(orderId);
        verify(orderService, never()).createOrderFromCartAndCompleteCheckoutAttempt(
                anyString(),
                anyString(),
                any(),
                any(),
                anyString(),
                any(),
                any(),
                any()
        );
    }

    @Test
    void managerLinkCreatesOrderWithIdempotencyAndCopyOnlyStatus() {
        OrderService orderService = mock(OrderService.class);
        CustomerService customerService = mock(CustomerService.class);
        PaymentService paymentService = mock(PaymentService.class);
        EmailService emailService = mock(EmailService.class);
        DirectusAdminService directusAdminService = mock(DirectusAdminService.class);
        OrderController controller = new OrderController(
                orderService,
                customerService,
                paymentService,
                emailService,
                directusAdminService,
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

        when(orderService.acquireCheckoutAttempt(eq("manager-key-1"), anyString()))
                .thenReturn(OrderService.CheckoutAttemptState.reserved());
        when(customerService.findOrCreateByEmail("customer@example.test", null, null)).thenReturn(customer);
        when(customerService.applyCheckoutContact(customer, "Customer", "+79990000000")).thenReturn(customer);
        when(orderService.createOrderFromCartAndCompleteCheckoutAttempt(
                eq("manager-key-1"),
                anyString(),
                eq(cartId),
                eq(customerId),
                eq("customer@example.test"),
                eq("manager-subject"),
                any(OrderService.ContactSpec.class)
        )).thenReturn(order);

        ResponseEntity<OrderController.OrderLinkResponse> response = controller.createManagerLink(
                new OrderController.ManagerLinkRequest(
                        cartId,
                        "customer@example.test",
                        "Customer",
                        "+79990000000",
                        "Address",
                        "https://yug-postel.ru/order/{token}",
                        false,
                        "manager-key-1"
                ),
                managerJwt()
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().orderId()).isEqualTo(orderId);
        assertThat(response.getBody().publicToken()).isEqualTo("public-token");
        assertThat(response.getBody().orderUrl()).isEqualTo("https://yug-postel.ru/order/public-token");
        assertThat(response.getBody().emailSent()).isFalse();
        verify(emailService, never()).sendOrderCreatedEmail(any(), anyString(), any());
        verify(directusAdminService).recordManagerPaymentLink(order, "manager-subject", "manager@example.test", false);
    }

    @Test
    void managerLinkReplayDoesNotSendDuplicateEmail() {
        OrderService orderService = mock(OrderService.class);
        CustomerService customerService = mock(CustomerService.class);
        PaymentService paymentService = mock(PaymentService.class);
        EmailService emailService = mock(EmailService.class);
        DirectusAdminService directusAdminService = mock(DirectusAdminService.class);
        OrderController controller = new OrderController(
                orderService,
                customerService,
                paymentService,
                emailService,
                directusAdminService,
                mock(NotificationOrchestrator.class)
        );
        UUID cartId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        Order order = new Order(customerId, "PENDING", Money.of(150000L, "RUB"));
        order.setId(orderId);
        order.setPublicToken("public-token");

        when(orderService.acquireCheckoutAttempt(eq("manager-key-1"), anyString()))
                .thenReturn(OrderService.CheckoutAttemptState.completed(orderId));
        when(orderService.findOrderByCheckoutAttempt(eq("manager-key-1"), anyString())).thenReturn(order);
        when(directusAdminService.isManagerPaymentLinkSent(orderId)).thenReturn(true);

        ResponseEntity<OrderController.OrderLinkResponse> response = controller.createManagerLink(
                new OrderController.ManagerLinkRequest(
                        cartId,
                        "customer@example.test",
                        "Customer",
                        "+79990000000",
                        "Address",
                        "https://yug-postel.ru/order/{token}",
                        true,
                        "manager-key-1"
                ),
                managerJwt()
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().emailSent()).isTrue();
        verify(emailService, never()).sendOrderCreatedEmail(any(), anyString(), any());
        verify(directusAdminService).recordManagerPaymentLink(order, "manager-subject", "manager@example.test", true);
        verify(orderService, never()).createOrderFromCartAndCompleteCheckoutAttempt(
                anyString(),
                anyString(),
                any(),
                any(),
                anyString(),
                anyString(),
                any()
        );
    }

    @Test
    void publicPaymentRefreshMarksManagerPaymentLinkPaidWhenPaymentCompletes() {
        OrderService orderService = mock(OrderService.class);
        PaymentService paymentService = mock(PaymentService.class);
        DirectusAdminService directusAdminService = mock(DirectusAdminService.class);
        OrderController controller = new OrderController(
                orderService,
                mock(CustomerService.class),
                paymentService,
                mock(EmailService.class),
                directusAdminService,
                mock(NotificationOrchestrator.class)
        );
        UUID customerId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        Order order = new Order(customerId, "PAID", Money.of(150000L, "RUB"));
        order.setId(orderId);
        order.setPublicToken("public-token");
        Payment payment = new Payment(orderId, Money.of(150000L, "RUB"), "YOOKASSA", PaymentStatus.COMPLETED);

        when(orderService.findByPublicToken("public-token")).thenReturn(order);
        when(paymentService.refreshLatestYooKassaPaymentForOrder(orderId))
                .thenReturn(new PaymentService.PaymentUpdateResult(payment, true));

        ResponseEntity<Order> response = controller.refreshPaymentByToken("public-token");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(directusAdminService).markManagerPaymentLinkPaid(orderId);
    }

    private Jwt managerJwt() {
        return new Jwt(
                "token",
                Instant.parse("2026-05-11T00:00:00Z"),
                Instant.parse("2026-05-11T01:00:00Z"),
                Map.of("alg", "none"),
                Map.of(
                        "sub", "manager-subject",
                        "email", "manager@example.test",
                        "preferred_username", "manager@example.test"
                )
        );
    }
}
