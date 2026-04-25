package com.example.api.notification;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.util.HtmlUtils;

import java.util.Locale;

@Service
public class NotificationTemplateService {
    private final ObjectMapper objectMapper;

    public NotificationTemplateService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public RenderedNotification render(NotificationType type, String payloadJson) {
        JsonNode payload = readPayload(payloadJson);
        return switch (type) {
            case ORDER_PAID -> renderOrderPaid(payload);
            case ORDER_SHIPPED -> renderOrderShipped(payload);
            case RMA_DECISION -> renderRmaDecision(payload);
            case ORDER_DELIVERED -> renderStatusUpdate(payload, "Доставлен", "Ваш заказ доставлен.");
            case ORDER_RECEIVED -> renderStatusUpdate(payload, "Получен", "Заказ отмечен как полученный.");
        };
    }

    public String statusLabel(String status) {
        if (!StringUtils.hasText(status)) {
            return "Неизвестно";
        }
        return switch (status.trim().toUpperCase(Locale.ROOT)) {
            case "PENDING" -> "Ожидает оплаты";
            case "PAID" -> "Оплачен";
            case "PROCESSING" -> "В обработке";
            case "READY_FOR_PICKUP" -> "Готов к выдаче";
            case "SHIPPED" -> "Отгружен";
            case "DELIVERED" -> "Доставлен";
            case "RECEIVED", "COMPLETED" -> "Получен";
            case "CANCELLED" -> "Отменен";
            case "REFUNDED" -> "Возвращен";
            default -> status;
        };
    }

    public String rmaDecisionLabel(String status) {
        if (!StringUtils.hasText(status)) {
            return "Решение не указано";
        }
        return switch (status.trim().toUpperCase(Locale.ROOT)) {
            case "APPROVED" -> "Одобрен";
            case "REJECTED" -> "Отклонен";
            default -> status;
        };
    }

    private RenderedNotification renderOrderPaid(JsonNode payload) {
        String orderNumber = orderNumber(payload);
        String subject = "Заказ " + orderNumber + " оплачен";
        StringBuilder html = new StringBuilder();
        html.append(paragraph("Мы получили оплату по вашему заказу. Менеджер свяжется с вами для согласования доставки."));
        html.append(orderSummaryHtml(payload));
        html.append(sectionTitle("Чек 54-ФЗ"));
        String registration = text(payload, "receiptRegistration");
        if (StringUtils.hasText(registration)) {
            html.append(paragraph("Статус регистрации чека: " + registration));
        } else {
            html.append(paragraph("Чек формируется платежным провайдером и отправляется на email покупателя."));
        }
        String receiptUrl = text(payload, "receiptUrl");
        if (StringUtils.hasText(receiptUrl)) {
            html.append(action("Открыть чек", receiptUrl));
        }
        String providerPaymentId = text(payload, "providerPaymentId");
        if (StringUtils.hasText(providerPaymentId)) {
            html.append(paragraph("ID платежа: " + providerPaymentId));
        }

        StringBuilder text = new StringBuilder();
        text.append("Заказ ").append(orderNumber).append(" оплачен.\n");
        text.append("Мы получили оплату по вашему заказу. Менеджер свяжется с вами для согласования доставки.\n\n");
        text.append(orderSummaryText(payload));
        text.append("\nЧек 54-ФЗ\n");
        text.append(StringUtils.hasText(registration)
                ? "Статус регистрации чека: " + registration + "\n"
                : "Чек формируется платежным провайдером и отправляется на email покупателя.\n");
        if (StringUtils.hasText(receiptUrl)) {
            text.append("Ссылка на чек: ").append(receiptUrl).append('\n');
        }
        if (StringUtils.hasText(providerPaymentId)) {
            text.append("ID платежа: ").append(providerPaymentId).append('\n');
        }
        return layout(subject, html.toString(), text.toString());
    }

    private RenderedNotification renderOrderShipped(JsonNode payload) {
        String orderNumber = orderNumber(payload);
        String subject = "Заказ " + orderNumber + " отгружен";
        String carrier = fallback(text(payload, "carrier"), "Перевозчик не указан");
        String trackingNumber = fallback(text(payload, "trackingNumber"), "Трек-номер не указан");
        String trackingUrl = text(payload, "trackingUrl");

        StringBuilder html = new StringBuilder();
        html.append(paragraph("Заказ передан в доставку."));
        html.append(orderSummaryHtml(payload));
        html.append(sectionTitle("Отслеживание"));
        html.append(definition("Перевозчик", carrier));
        html.append(definition("Трек-номер", trackingNumber));
        if (StringUtils.hasText(trackingUrl)) {
            html.append(action("Отследить заказ", trackingUrl));
        }

        StringBuilder text = new StringBuilder();
        text.append("Заказ ").append(orderNumber).append(" отгружен.\n\n");
        text.append(orderSummaryText(payload));
        text.append("\nОтслеживание\n");
        text.append("Перевозчик: ").append(carrier).append('\n');
        text.append("Трек-номер: ").append(trackingNumber).append('\n');
        if (StringUtils.hasText(trackingUrl)) {
            text.append("Ссылка: ").append(trackingUrl).append('\n');
        }
        return layout(subject, html.toString(), text.toString());
    }

    private RenderedNotification renderRmaDecision(JsonNode payload) {
        String rmaNumber = fallback(text(payload, "rmaNumber"), text(payload, "rmaId"));
        String decisionStatus = text(payload, "decisionStatus");
        String decisionLabel = fallback(text(payload, "decisionStatusLabel"), rmaDecisionLabel(decisionStatus));
        String comment = text(payload, "managerComment");
        String subject = "Решение по возврату " + rmaNumber + ": " + decisionLabel;

        StringBuilder html = new StringBuilder();
        html.append(paragraph("Менеджер рассмотрел запрос на возврат."));
        html.append(orderSummaryHtml(payload));
        html.append(sectionTitle("Решение"));
        html.append(definition("Запрос", rmaNumber));
        html.append(definition("Статус", decisionLabel));
        html.append(definition("Комментарий менеджера", fallback(comment, "Комментарий не указан")));

        StringBuilder text = new StringBuilder();
        text.append("Решение по возврату ").append(rmaNumber).append(": ").append(decisionLabel).append("\n\n");
        text.append(orderSummaryText(payload));
        text.append("\nЗапрос: ").append(rmaNumber).append('\n');
        text.append("Статус: ").append(decisionLabel).append('\n');
        text.append("Комментарий менеджера: ").append(fallback(comment, "Комментарий не указан")).append('\n');
        return layout(subject, html.toString(), text.toString());
    }

    private RenderedNotification renderStatusUpdate(JsonNode payload, String label, String intro) {
        String orderNumber = orderNumber(payload);
        String subject = "Статус заказа " + orderNumber + ": " + label;
        StringBuilder html = new StringBuilder();
        html.append(paragraph(intro));
        html.append(orderSummaryHtml(payload));

        StringBuilder text = new StringBuilder();
        text.append("Статус заказа ").append(orderNumber).append(": ").append(label).append('\n');
        text.append(intro).append("\n\n");
        text.append(orderSummaryText(payload));
        return layout(subject, html.toString(), text.toString());
    }

    private RenderedNotification layout(String subject, String contentHtml, String textBody) {
        String html = "<!doctype html><html lang=\"ru\"><head><meta charset=\"UTF-8\">"
                + "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">"
                + "<title>" + h(subject) + "</title></head>"
                + "<body style=\"margin:0;padding:0;background:#f6f2ed;color:#2b2221;font-family:Arial,sans-serif;\">"
                + "<table role=\"presentation\" width=\"100%\" cellspacing=\"0\" cellpadding=\"0\" style=\"padding:24px 0;\">"
                + "<tr><td align=\"center\"><table role=\"presentation\" width=\"640\" cellspacing=\"0\" cellpadding=\"0\" "
                + "style=\"max-width:640px;width:100%;background:#fff;border:1px solid #eee3db;\">"
                + "<tr><td style=\"padding:26px 30px 8px;\">"
                + "<div style=\"font-size:12px;letter-spacing:.12em;text-transform:uppercase;color:#8f837b;\">ПОСТЕЛЬНОЕ БЕЛЬЕ-ЮГ</div>"
                + "<h1 style=\"margin:12px 0 16px;font-size:26px;line-height:1.25;color:#2b2221;\">" + h(subject) + "</h1>"
                + contentHtml
                + "</td></tr><tr><td style=\"padding:10px 30px 26px;color:#8b817b;font-size:12px;line-height:1.5;\">"
                + "Это транзакционное уведомление по вашему заказу.</td></tr></table></td></tr></table>"
                + "</body></html>";
        return new RenderedNotification(subject, html, textBody);
    }

    private String orderSummaryHtml(JsonNode payload) {
        StringBuilder html = new StringBuilder();
        html.append(sectionTitle("Заказ"));
        html.append(definition("Номер заказа", orderNumber(payload)));
        html.append(definition("Статус", statusLabel(fallback(text(payload, "status"), text(payload, "orderStatus")))));
        html.append(definition("Сумма", amountLabel(payload)));
        JsonNode items = payload.path("items");
        if (items.isArray() && !items.isEmpty()) {
            html.append("<table role=\"presentation\" width=\"100%\" cellspacing=\"0\" cellpadding=\"0\" style=\"border-collapse:collapse;margin:12px 0 18px;font-size:14px;\">");
            for (JsonNode item : items) {
                html.append("<tr>")
                        .append("<td style=\"border-top:1px solid #eee8e3;padding:10px 0;color:#342f2c;\">")
                        .append(h(itemName(item)))
                        .append("</td><td align=\"right\" style=\"border-top:1px solid #eee8e3;padding:10px 0;color:#5d5753;white-space:nowrap;\">")
                        .append(h(itemQuantity(item))).append(" x ").append(h(itemAmount(item)))
                        .append("</td></tr>");
            }
            html.append("</table>");
        }
        return html.toString();
    }

    private String orderSummaryText(JsonNode payload) {
        StringBuilder text = new StringBuilder();
        text.append("Номер заказа: ").append(orderNumber(payload)).append('\n');
        text.append("Статус: ").append(statusLabel(fallback(text(payload, "status"), text(payload, "orderStatus")))).append('\n');
        text.append("Сумма: ").append(amountLabel(payload)).append('\n');
        JsonNode items = payload.path("items");
        if (items.isArray() && !items.isEmpty()) {
            text.append("Товары:\n");
            for (JsonNode item : items) {
                text.append("- ")
                        .append(itemName(item))
                        .append(" x ")
                        .append(itemQuantity(item))
                        .append(" = ")
                        .append(itemAmount(item))
                        .append('\n');
            }
        }
        return text.toString();
    }

    private JsonNode readPayload(String payloadJson) {
        if (!StringUtils.hasText(payloadJson)) {
            return objectMapper.createObjectNode();
        }
        try {
            return objectMapper.readTree(payloadJson);
        } catch (Exception ex) {
            return objectMapper.createObjectNode();
        }
    }

    private String sectionTitle(String value) {
        return "<h2 style=\"margin:18px 0 8px;font-size:16px;line-height:1.35;color:#2b2221;\">" + h(value) + "</h2>";
    }

    private String paragraph(String value) {
        return "<p style=\"margin:0 0 12px;color:#4f4a46;font-size:14px;line-height:1.55;\">" + h(value) + "</p>";
    }

    private String definition(String label, String value) {
        return "<p style=\"margin:0 0 8px;color:#4f4a46;font-size:14px;line-height:1.5;\"><strong>"
                + h(label)
                + ":</strong> "
                + h(value)
                + "</p>";
    }

    private String action(String label, String url) {
        return "<p style=\"margin:16px 0;\"><a href=\""
                + h(url)
                + "\" style=\"display:inline-block;background:#b65b4a;color:#fff;text-decoration:none;padding:10px 16px;font-weight:700;\">"
                + h(label)
                + "</a></p>";
    }

    private String orderNumber(JsonNode payload) {
        return fallback(text(payload, "orderNumber"), text(payload, "orderId"));
    }

    private String amountLabel(JsonNode payload) {
        String amount = text(payload, "amount");
        String currency = text(payload, "currency");
        String formatted = text(payload, "amountFormatted");
        if (StringUtils.hasText(formatted)) {
            return formatted;
        }
        if (StringUtils.hasText(amount) && StringUtils.hasText(currency)) {
            return amount + " " + currency;
        }
        return StringUtils.hasText(amount) ? amount : "Не указана";
    }

    private String itemName(JsonNode item) {
        String name = fallback(text(item, "name"), text(item, "productName"));
        String variant = text(item, "variantName");
        return StringUtils.hasText(variant) ? name + " (" + variant + ")" : name;
    }

    private String itemQuantity(JsonNode item) {
        String value = text(item, "quantity");
        return StringUtils.hasText(value) ? value : "1";
    }

    private String itemAmount(JsonNode item) {
        String formatted = text(item, "unitAmountFormatted");
        if (StringUtils.hasText(formatted)) {
            return formatted;
        }
        String amount = text(item, "unitAmount");
        String currency = text(item, "currency");
        if (StringUtils.hasText(amount) && StringUtils.hasText(currency)) {
            return amount + " " + currency;
        }
        return fallback(amount, "Не указана");
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node != null ? node.get(field) : null;
        if (value == null || value.isNull()) {
            return null;
        }
        return value.isTextual() ? value.asText() : value.asText(null);
    }

    private String fallback(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }

    private String h(String value) {
        return HtmlUtils.htmlEscape(value == null ? "" : value);
    }
}
