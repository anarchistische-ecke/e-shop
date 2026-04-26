package com.example.api.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationTemplateServiceTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final NotificationTemplateService service = new NotificationTemplateService(objectMapper);

    @Test
    void orderPaidTemplateIncludesReceiptFieldsAndEscapesHtml() throws Exception {
        String orderId = UUID.randomUUID().toString();
        String payload = objectMapper.writeValueAsString(Map.of(
                "orderId", orderId,
                "orderNumber", orderId,
                "status", "PAID",
                "amountFormatted", "15000.00 RUB",
                "receiptRegistration", "succeeded",
                "receiptUrl", "https://receipt.example.test/check/1",
                "providerPaymentId", "pay-1",
                "items", List.of(Map.of(
                        "name", "<Комплект>",
                        "quantity", 1,
                        "unitAmountFormatted", "15000.00 RUB"
                ))
        ));

        RenderedNotification rendered = service.render(NotificationType.ORDER_PAID, payload);

        assertThat(rendered.subject()).contains("оплачен");
        assertThat(rendered.htmlBody()).contains("Чек 54-ФЗ");
        assertThat(rendered.htmlBody()).contains("succeeded");
        assertThat(rendered.htmlBody()).contains("https://receipt.example.test/check/1");
        assertThat(rendered.htmlBody()).contains("&lt;Комплект&gt;");
        assertThat(rendered.htmlBody()).doesNotContain("<Комплект>");
        assertThat(rendered.textBody()).contains("ID платежа: pay-1");
        assertThat(rendered.textBody()).contains("<Комплект>");
    }

    @Test
    void shippedTemplateHandlesMissingOptionalTrackingUrl() throws Exception {
        String payload = objectMapper.writeValueAsString(Map.of(
                "orderId", "order-1",
                "status", "SHIPPED",
                "amountFormatted", "100.00 RUB",
                "carrier", "CDEK",
                "trackingNumber", "TRACK-1"
        ));

        RenderedNotification rendered = service.render(NotificationType.ORDER_SHIPPED, payload);

        assertThat(rendered.htmlBody()).contains("CDEK");
        assertThat(rendered.htmlBody()).contains("TRACK-1");
        assertThat(rendered.htmlBody()).doesNotContain("href=\"\"");
        assertThat(rendered.textBody()).contains("Трек-номер: TRACK-1");
    }

    @Test
    void rmaDecisionTemplateIncludesDecisionAndManagerComment() throws Exception {
        String payload = objectMapper.writeValueAsString(Map.of(
                "orderId", "order-1",
                "rmaNumber", "RMA-1",
                "decisionStatus", "APPROVED",
                "decisionStatusLabel", "Одобрен",
                "managerComment", "Можно вернуть через пункт выдачи"
        ));

        RenderedNotification rendered = service.render(NotificationType.RMA_DECISION, payload);

        assertThat(rendered.subject()).contains("RMA-1").contains("Одобрен");
        assertThat(rendered.htmlBody()).contains("Можно вернуть через пункт выдачи");
        assertThat(rendered.textBody()).contains("Комментарий менеджера: Можно вернуть через пункт выдачи");
    }

    @Test
    void statusLabelsMapReceivedAndLegacyCompletedToRussianReceived() {
        assertThat(service.statusLabel("RECEIVED")).isEqualTo("Получен");
        assertThat(service.statusLabel("COMPLETED")).isEqualTo("Получен");
        assertThat(service.statusLabel("READY_FOR_PICKUP")).isEqualTo("Готов к выдаче");
    }
}
