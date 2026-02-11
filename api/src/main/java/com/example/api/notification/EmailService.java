package com.example.api.notification;

import com.example.common.domain.Money;
import com.example.order.domain.Order;
import com.example.order.domain.OrderItem;
import com.example.payment.domain.Payment;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.util.HtmlUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;

@Service
public class EmailService {
    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;

    @Value("${mail.from:yug-postel@yandex.ru}")
    private String fromAddress;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public void sendOrderCreatedEmail(Order order, String toEmail, String orderUrl) {
        if (!StringUtils.hasText(toEmail) || order == null) {
            return;
        }
        String subject = "Заказ создан";
        String subtitle = "Спасибо за заказ в магазине Постельное Белье-ЮГ.";
        String html = buildEmailLayout(
                subject,
                subtitle,
                order,
                null,
                "Оплатить заказ",
                orderUrl
        );
        StringBuilder text = new StringBuilder();
        text.append(subtitle).append('\n').append('\n');
        text.append(buildOrderSummaryText(order));
        if (StringUtils.hasText(orderUrl)) {
            text.append('\n').append("Оплатить заказ: ").append(orderUrl).append('\n');
        }
        sendEmail(toEmail, subject, html, text.toString());
    }

    public void sendPaymentReceipt(Order order, Payment payment, String toEmail) {
        if (!StringUtils.hasText(toEmail) || order == null) {
            return;
        }
        String subject = "Ваш чек по заказу";
        String subtitle = "Заказ успешно оплачен. Спасибо за покупку!";
        String extra = null;
        if (payment != null && StringUtils.hasText(payment.getProviderPaymentId())) {
            extra = "<p style=\"margin:0 0 16px;color:#4f4a46;font-size:14px;\">ID платежа: "
                    + escapeHtml(payment.getProviderPaymentId())
                    + "</p>";
        }
        String html = buildEmailLayout(subject, subtitle, order, extra, null, null);
        StringBuilder text = new StringBuilder();
        text.append(subtitle).append('\n').append('\n');
        text.append(buildOrderSummaryText(order));
        if (payment != null && StringUtils.hasText(payment.getProviderPaymentId())) {
            text.append('\n').append("ID платежа: ").append(payment.getProviderPaymentId()).append('\n');
        }
        sendEmail(toEmail, subject, html, text.toString());
    }

    public void sendOrderStatusUpdatedEmail(Order order, String toEmail, String previousStatus) {
        if (!StringUtils.hasText(toEmail) || order == null) {
            return;
        }
        String currentStatus = statusLabel(order.getStatus());
        String subject = "Статус заказа: " + currentStatus;

        StringBuilder details = new StringBuilder();
        details.append("<p style=\"margin:0 0 16px;color:#4f4a46;font-size:14px;\">");
        if (StringUtils.hasText(previousStatus)) {
            details.append("Статус изменен: <strong>")
                    .append(escapeHtml(statusLabel(previousStatus)))
                    .append("</strong> -> <strong>")
                    .append(escapeHtml(currentStatus))
                    .append("</strong>");
        } else {
            details.append("Текущий статус заказа: <strong>")
                    .append(escapeHtml(currentStatus))
                    .append("</strong>");
        }
        details.append("</p>");

        String html = buildEmailLayout(
                subject,
                "Мы обновили информацию по вашему заказу.",
                order,
                details.toString(),
                null,
                null
        );

        StringBuilder text = new StringBuilder();
        text.append("Мы обновили информацию по вашему заказу.").append('\n');
        if (StringUtils.hasText(previousStatus)) {
            text.append("Статус: ")
                    .append(statusLabel(previousStatus))
                    .append(" -> ")
                    .append(currentStatus)
                    .append('\n');
        } else {
            text.append("Статус: ").append(currentStatus).append('\n');
        }
        text.append('\n').append(buildOrderSummaryText(order));
        sendEmail(toEmail, subject, html, text.toString());
    }

    private void sendEmail(String toEmail, String subject, String htmlBody, String textBody) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(toEmail);
            helper.setSubject(subject);
            helper.setText(textBody, htmlBody);
            mailSender.send(message);
        } catch (MailException | MessagingException ex) {
            log.warn("Failed to send email '{}' to {}", subject, toEmail, ex);
        }
    }

    private String buildOrderSummaryText(Order order) {
        StringBuilder body = new StringBuilder();
        body.append("Номер заказа: ").append(order.getId()).append('\n');
        body.append("Статус заказа: ").append(statusLabel(order.getStatus())).append('\n');
        body.append("Сумма: ").append(formatMoney(order.getTotalAmount())).append(" ").append(order.getTotalAmount().getCurrency()).append('\n');
        body.append("Товары:\n");
        for (OrderItem item : order.getItems()) {
            body.append("- ")
                .append(itemLabel(item))
                .append(" x ")
                .append(item.getQuantity())
                .append(" = ")
                .append(formatMoney(item.getUnitPrice()))
                .append(" ")
                .append(item.getUnitPrice().getCurrency())
                .append('\n');
        }
        return body.toString();
    }

    private String buildEmailLayout(String title,
                                    String subtitle,
                                    Order order,
                                    String extraHtml,
                                    String actionLabel,
                                    String actionUrl) {
        StringBuilder html = new StringBuilder();
        html.append("<!doctype html><html lang=\"ru\"><head><meta charset=\"UTF-8\">")
                .append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">")
                .append("<title>").append(escapeHtml(title)).append("</title>")
                .append("</head>")
                .append("<body style=\"margin:0;padding:0;background:#f4efe9;color:#2b2221;")
                .append("font-family:'Segoe UI',Arial,sans-serif;\">")
                .append("<table role=\"presentation\" width=\"100%\" cellspacing=\"0\" cellpadding=\"0\" style=\"padding:24px 0;\">")
                .append("<tr><td align=\"center\">")
                .append("<table role=\"presentation\" width=\"640\" cellspacing=\"0\" cellpadding=\"0\" ")
                .append("style=\"max-width:640px;width:100%;background:#ffffff;border-radius:22px;overflow:hidden;")
                .append("box-shadow:0 10px 30px rgba(0,0,0,0.08);\">")
                .append("<tr><td style=\"padding:28px 32px 12px;\">")
                .append("<div style=\"font-size:12px;letter-spacing:0.24em;text-transform:uppercase;color:#9b9087;\">")
                .append("ПОСТЕЛЬНОЕ БЕЛЬЕ-ЮГ")
                .append("</div>")
                .append("<h1 style=\"margin:16px 0 10px;font-size:30px;line-height:1.15;")
                .append("font-family:'Times New Roman',Georgia,serif;font-weight:600;color:#2b2221;\">")
                .append(escapeHtml(title))
                .append("</h1>")
                .append("<p style=\"margin:0 0 14px;color:#4f4a46;font-size:16px;line-height:1.5;\">")
                .append(escapeHtml(subtitle))
                .append("</p>");

        if (StringUtils.hasText(extraHtml)) {
            html.append(extraHtml);
        }

        html.append(buildOrderSummaryHtml(order));

        if (StringUtils.hasText(actionLabel) && StringUtils.hasText(actionUrl)) {
            html.append("<div style=\"margin:24px 0 8px;\">")
                    .append("<a href=\"").append(escapeHtml(actionUrl)).append("\" ")
                    .append("style=\"display:inline-block;background:#b65b4a;color:#ffffff;text-decoration:none;")
                    .append("padding:12px 22px;border-radius:999px;font-weight:600;font-size:15px;\">")
                    .append(escapeHtml(actionLabel))
                    .append("</a>")
                    .append("</div>");
        }

        html.append("</td></tr>")
                .append("<tr><td style=\"padding:0 32px 26px;color:#8b817b;font-size:12px;line-height:1.5;\">")
                .append("Если вы не совершали этот запрос, просто проигнорируйте это письмо.")
                .append("</td></tr>")
                .append("</table>")
                .append("</td></tr></table>")
                .append("</body></html>");
        return html.toString();
    }

    private String buildOrderSummaryHtml(Order order) {
        StringBuilder html = new StringBuilder();
        html.append("<div style=\"margin-top:16px;border:1px solid #eee8e3;border-radius:16px;overflow:hidden;\">")
                .append("<div style=\"padding:14px 16px;background:#fbf8f5;font-size:14px;color:#5e5550;\">")
                .append("<strong>Номер заказа:</strong> ")
                .append(escapeHtml(String.valueOf(order.getId())))
                .append("&nbsp;&nbsp;|&nbsp;&nbsp;<strong>Статус:</strong> ")
                .append(escapeHtml(statusLabel(order.getStatus())))
                .append("</div>")
                .append("<table role=\"presentation\" width=\"100%\" cellspacing=\"0\" cellpadding=\"0\" ")
                .append("style=\"border-collapse:collapse;font-size:14px;\">");

        for (OrderItem item : order.getItems()) {
            html.append("<tr>")
                    .append("<td style=\"padding:11px 16px;border-top:1px solid #f0ebe7;color:#342f2c;\">")
                    .append(escapeHtml(itemLabel(item)))
                    .append("</td>")
                    .append("<td align=\"right\" style=\"padding:11px 16px;border-top:1px solid #f0ebe7;color:#5d5753;white-space:nowrap;\">")
                    .append(item.getQuantity())
                    .append(" x ")
                    .append(escapeHtml(formatMoney(item.getUnitPrice())))
                    .append(" ")
                    .append(escapeHtml(item.getUnitPrice().getCurrency()))
                    .append("</td>")
                    .append("</tr>");
        }

        html.append("<tr>")
                .append("<td style=\"padding:13px 16px;border-top:1px solid #ece6e1;font-weight:700;\">Итого</td>")
                .append("<td align=\"right\" style=\"padding:13px 16px;border-top:1px solid #ece6e1;font-weight:700;white-space:nowrap;\">")
                .append(escapeHtml(formatMoney(order.getTotalAmount())))
                .append(" ")
                .append(escapeHtml(order.getTotalAmount().getCurrency()))
                .append("</td>")
                .append("</tr>")
                .append("</table></div>");
        return html.toString();
    }

    private String itemLabel(OrderItem item) {
        String product = item.getProductName() != null && !item.getProductName().isBlank()
                ? item.getProductName()
                : "Товар";
        String variant = item.getVariantName() != null && !item.getVariantName().isBlank()
                ? " (" + item.getVariantName() + ")"
                : "";
        return product + variant;
    }

    private String formatMoney(Money money) {
        if (money == null) {
            return "0.00";
        }
        BigDecimal value = money.toBigDecimal();
        return value.setScale(2, RoundingMode.HALF_UP).toString();
    }

    private String statusLabel(String status) {
        if (!StringUtils.hasText(status)) {
            return "Неизвестно";
        }
        return switch (status.toUpperCase(Locale.ROOT)) {
            case "PENDING" -> "Ожидает оплаты";
            case "PAID" -> "Оплачен";
            case "CANCELLED" -> "Отменен";
            case "REFUNDED" -> "Возвращен";
            case "SHIPPED" -> "Отгружен";
            case "DELIVERED" -> "Доставлен";
            default -> status;
        };
    }

    private String escapeHtml(String value) {
        return HtmlUtils.htmlEscape(value == null ? "" : value);
    }
}
