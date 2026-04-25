package com.example.api.notification;

import com.example.common.domain.Money;
import com.example.order.domain.Order;
import com.example.order.domain.OrderItem;
import com.example.payment.domain.Payment;
import com.example.payment.domain.PaymentStatus;
import com.example.shipment.domain.Shipment;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class NotificationOrchestratorTest {

    @Test
    void orderPaidEnqueuesPaymentReceiptPayloadOncePerPayment() {
        NotificationService notificationService = mock(NotificationService.class);
        NotificationOrchestrator orchestrator = orchestrator(notificationService);
        Order order = order("PAID");
        Payment payment = new Payment(order.getId(), order.getTotalAmount(), "YOOKASSA", PaymentStatus.COMPLETED);
        payment.setProviderPaymentId("pay-1");
        payment.setReceiptRegistration("succeeded");
        payment.setReceiptUrl("https://receipt.example.test/1");

        orchestrator.orderPaid(order, payment);

        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(notificationService).enqueueOnce(
                eq(NotificationType.ORDER_PAID),
                eq("ORDER_PAID:" + order.getId() + ":pay-1"),
                eq("ORDER"),
                eq(order.getId()),
                eq("buyer@example.test"),
                payloadCaptor.capture()
        );
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) payloadCaptor.getValue();
        assertThat(payload).containsEntry("receiptRegistration", "succeeded");
        assertThat(payload).containsEntry("receiptUrl", "https://receipt.example.test/1");
        assertThat(payload.get("items")).asList().hasSize(1);
    }

    @Test
    void orderShippedUsesConfiguredTrackingTemplate() {
        NotificationService notificationService = mock(NotificationService.class);
        NotificationOrchestrator orchestrator = orchestrator(notificationService);
        Order order = order("SHIPPED");
        Shipment shipment = new Shipment(order.getId(), "CDEK", "ABC 123");
        shipment.setId(UUID.randomUUID());

        orchestrator.orderShipped(order, shipment);

        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(notificationService).enqueueOnce(
                eq(NotificationType.ORDER_SHIPPED),
                eq("ORDER_SHIPPED:" + shipment.getId()),
                eq("ORDER"),
                eq(order.getId()),
                eq("buyer@example.test"),
                payloadCaptor.capture()
        );
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) payloadCaptor.getValue();
        assertThat(payload.get("trackingUrl")).isEqualTo("https://track.example.test/ABC%20123");
    }

    private NotificationOrchestrator orchestrator(NotificationService notificationService) {
        NotificationProperties properties = new NotificationProperties();
        properties.setTrackingUrlTemplates(Map.of("cdek", "https://track.example.test/{trackingNumber}"));
        return new NotificationOrchestrator(
                notificationService,
                properties,
                new NotificationTemplateService(new ObjectMapper())
        );
    }

    private Order order(String status) {
        Order order = new Order(UUID.randomUUID(), status, Money.of(1500000, "RUB"));
        order.setId(UUID.randomUUID());
        order.setReceiptEmail("buyer@example.test");
        OrderItem item = new OrderItem(UUID.randomUUID(), 1, Money.of(1500000, "RUB"));
        item.setId(UUID.randomUUID());
        item.setProductName("Комплект");
        order.addItem(item);
        return order;
    }
}
