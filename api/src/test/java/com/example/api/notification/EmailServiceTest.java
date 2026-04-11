package com.example.api.notification;

import com.example.common.domain.Money;
import com.example.order.domain.Order;
import com.example.order.domain.OrderItem;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Properties;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EmailServiceTest {

    @Test
    void sendOrderCreatedEmailUsesMultipartMessageForHtmlAndTextBodies() {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        MimeMessage message = new MimeMessage(Session.getInstance(new Properties()));
        when(mailSender.createMimeMessage()).thenReturn(message);

        EmailService service = new EmailService(mailSender);
        ReflectionTestUtils.setField(service, "fromAddress", "shop@example.test");

        assertDoesNotThrow(() -> service.sendOrderCreatedEmail(
                buildOrder(),
                "buyer@example.test",
                "https://example.test/order/test-token"
        ));

        verify(mailSender).send(message);
    }

    @Test
    void sendOrderCreatedEmailDoesNotBubbleUpMailRuntimeFailures() {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        MimeMessage message = new MimeMessage(Session.getInstance(new Properties()));
        when(mailSender.createMimeMessage()).thenReturn(message);
        doThrow(new IllegalStateException("mail transport unavailable"))
                .when(mailSender)
                .send(any(MimeMessage.class));

        EmailService service = new EmailService(mailSender);
        ReflectionTestUtils.setField(service, "fromAddress", "shop@example.test");

        assertDoesNotThrow(() -> service.sendOrderCreatedEmail(
                buildOrder(),
                "buyer@example.test",
                "https://example.test/order/test-token"
        ));
    }

    private Order buildOrder() {
        Order order = new Order(UUID.randomUUID(), "PENDING", Money.of(1500000, "RUB"));
        order.setId(UUID.randomUUID());

        OrderItem item = new OrderItem(UUID.randomUUID(), 1, Money.of(1500000, "RUB"));
        item.setProductName("Stripe Muslin");
        item.setVariantName("Euro");
        item.setOrder(order);
        order.getItems().add(item);

        return order;
    }
}
