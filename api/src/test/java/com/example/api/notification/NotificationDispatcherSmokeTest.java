package com.example.api.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessagePreparator;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.InputStream;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class NotificationDispatcherSmokeTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void dispatchesFiveNtfTemplatesThroughCapturedMailSender() throws Exception {
        List<NotificationOutbox> outboxRows = List.of(
                outbox(NotificationType.ORDER_PAID, "paid", Map.of(
                        "orderId", "order-1",
                        "status", "PAID",
                        "amountFormatted", "100.00 RUB",
                        "receiptRegistration", "succeeded",
                        "items", List.of(Map.of("name", "Комплект", "quantity", 1, "unitAmountFormatted", "100.00 RUB"))
                )),
                outbox(NotificationType.ORDER_SHIPPED, "shipped", Map.of(
                        "orderId", "order-1",
                        "status", "SHIPPED",
                        "amountFormatted", "100.00 RUB",
                        "carrier", "CDEK",
                        "trackingNumber", "TRACK-1",
                        "trackingUrl", "https://track.example.test/TRACK-1"
                )),
                outbox(NotificationType.RMA_DECISION, "rma", Map.of(
                        "orderId", "order-1",
                        "rmaNumber", "RMA-1",
                        "decisionStatus", "REJECTED",
                        "decisionStatusLabel", "Отклонен",
                        "managerComment", "Недостаточно фото"
                )),
                outbox(NotificationType.ORDER_DELIVERED, "delivered", Map.of(
                        "orderId", "order-1",
                        "status", "DELIVERED",
                        "amountFormatted", "100.00 RUB"
                )),
                outbox(NotificationType.ORDER_RECEIVED, "received", Map.of(
                        "orderId", "order-1",
                        "status", "RECEIVED",
                        "amountFormatted", "100.00 RUB"
                ))
        );
        NotificationOutboxRepository repository = mock(NotificationOutboxRepository.class);
        when(repository.findDue(anyCollection(), any(OffsetDateTime.class), eq(3), any(Pageable.class)))
                .thenReturn(outboxRows);
        CapturingMailSender mailSender = new CapturingMailSender();
        EmailService emailService = new EmailService(mailSender);
        ReflectionTestUtils.setField(emailService, "fromAddress", "shop@example.test");

        NotificationDispatcher dispatcher = new NotificationDispatcher(
                repository,
                new NotificationTemplateService(objectMapper),
                emailService,
                new NotificationProperties(),
                new SimpleMeterRegistry()
        );

        assertThat(dispatcher.dispatchDue()).isEqualTo(5);
        assertThat(mailSender.sentMessages).hasSize(5);
        assertThat(mailSender.sentMessages)
                .extracting(message -> {
                    try {
                        return message.getSubject();
                    } catch (Exception ex) {
                        throw new AssertionError(ex);
                    }
                })
                .containsExactly(
                        "Заказ order-1 оплачен",
                        "Заказ order-1 отгружен",
                        "Решение по возврату RMA-1: Отклонен",
                        "Статус заказа order-1: Доставлен",
                        "Статус заказа order-1: Получен"
                );
        assertThat(outboxRows).allMatch(row -> row.getStatus() == NotificationOutboxStatus.SENT);
    }

    private NotificationOutbox outbox(NotificationType type, String key, Map<String, Object> payload) throws Exception {
        NotificationOutbox outbox = new NotificationOutbox();
        outbox.setType(type);
        outbox.setEventKey(key);
        outbox.setRecipient("buyer@example.test");
        outbox.setStatus(NotificationOutboxStatus.PENDING);
        outbox.setPayloadJson(objectMapper.writeValueAsString(payload));
        outbox.setNextAttemptAt(OffsetDateTime.now().minusSeconds(1));
        return outbox;
    }

    private static class CapturingMailSender implements JavaMailSender {
        private final List<MimeMessage> sentMessages = new ArrayList<>();

        @Override
        public MimeMessage createMimeMessage() {
            return new MimeMessage(Session.getInstance(new Properties()));
        }

        @Override
        public MimeMessage createMimeMessage(InputStream contentStream) throws MailException {
            try {
                return new MimeMessage(Session.getInstance(new Properties()), contentStream);
            } catch (Exception ex) {
                throw new IllegalStateException(ex);
            }
        }

        @Override
        public void send(MimeMessage mimeMessage) throws MailException {
            sentMessages.add(mimeMessage);
        }

        @Override
        public void send(MimeMessage... mimeMessages) throws MailException {
            sentMessages.addAll(List.of(mimeMessages));
        }

        @Override
        public void send(MimeMessagePreparator mimeMessagePreparator) throws MailException {
            MimeMessage message = createMimeMessage();
            try {
                mimeMessagePreparator.prepare(message);
            } catch (Exception ex) {
                throw new IllegalStateException(ex);
            }
            send(message);
        }

        @Override
        public void send(MimeMessagePreparator... mimeMessagePreparators) throws MailException {
            for (MimeMessagePreparator preparator : mimeMessagePreparators) {
                send(preparator);
            }
        }

        @Override
        public void send(SimpleMailMessage simpleMessage) throws MailException {
            throw new UnsupportedOperationException("Simple messages are not used by transactional notifications");
        }

        @Override
        public void send(SimpleMailMessage... simpleMessages) throws MailException {
            throw new UnsupportedOperationException("Simple messages are not used by transactional notifications");
        }
    }
}
