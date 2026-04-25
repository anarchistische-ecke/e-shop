package com.example.api.notification;

import com.example.common.domain.Money;
import com.example.order.domain.Order;
import com.example.order.domain.OrderItem;
import com.example.payment.domain.Payment;
import com.example.payment.domain.PaymentStatus;
import jakarta.mail.BodyPart;
import jakarta.mail.Multipart;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Properties;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EmailServiceTest {

    @Test
    void sendOrderCreatedEmailUsesMultipartMessageForHtmlAndTextBodies() throws Exception {
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
        assertTrue(messageBody(message).contains("Финальную стоимость и варианты доставки согласует менеджер после оформления заказа."));
        assertTrue(messageBody(message).contains("Иван Петров"));
        assertTrue(messageBody(message).contains("+79990000000"));
        assertTrue(messageBody(message).contains("Москва, Тестовая улица, 1"));
    }

    @Test
    void sendPaymentReceiptIncludesManagerContactNotice() throws Exception {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        MimeMessage message = new MimeMessage(Session.getInstance(new Properties()));
        when(mailSender.createMimeMessage()).thenReturn(message);

        EmailService service = new EmailService(mailSender);
        ReflectionTestUtils.setField(service, "fromAddress", "shop@example.test");

        Payment payment = new Payment(UUID.randomUUID(), Money.of(1500000, "RUB"), "YOOKASSA", PaymentStatus.COMPLETED);
        payment.setProviderPaymentId("pay-test-1");

        assertDoesNotThrow(() -> service.sendPaymentReceipt(
                buildOrder(),
                payment,
                "buyer@example.test"
        ));

        verify(mailSender).send(message);
        String body = messageBody(message);
        assertTrue(body.contains("Наш менеджер свяжется с вами в ближайшее время."));
        assertTrue(body.contains("Финальную стоимость и варианты доставки согласует менеджер после оформления заказа."));
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
        order.setContactName("Иван Петров");
        order.setContactPhone("+79990000000");
        order.setHomeAddress("Москва, Тестовая улица, 1");

        OrderItem item = new OrderItem(UUID.randomUUID(), 1, Money.of(1500000, "RUB"));
        item.setProductName("Stripe Muslin");
        item.setVariantName("Euro");
        item.setOrder(order);
        order.getItems().add(item);

        return order;
    }

    private String messageBody(MimeMessage message) throws Exception {
        Object content = message.getContent();
        return collectContent(content);
    }

    private String collectContent(Object content) throws Exception {
        if (content instanceof String text) {
            return text;
        }
        if (content instanceof Multipart multipart) {
            StringBuilder builder = new StringBuilder();
            for (int index = 0; index < multipart.getCount(); index++) {
                BodyPart part = multipart.getBodyPart(index);
                builder.append(collectContent(part.getContent()));
            }
            return builder.toString();
        }
        return "";
    }
}
