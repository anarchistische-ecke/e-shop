package com.example.api.notification;

import com.example.common.domain.Money;
import com.example.order.domain.Order;
import com.example.order.domain.OrderItem;
import com.example.payment.domain.Payment;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
public class EmailService {
    private final JavaMailSender mailSender;

    @Value("${mail.from:no-reply@example.com}")
    private String fromAddress;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public void sendConfirmationCode(String toEmail, String code) {
        if (toEmail == null || toEmail.isBlank()) {
            return;
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(toEmail);
        message.setSubject("Your confirmation code");
        message.setText("Use this confirmation code to finish creating your account: " + code);
        mailSender.send(message);
    }

    public void sendOrderCreatedEmail(Order order, String toEmail, String orderUrl) {
        if (toEmail == null || toEmail.isBlank()) {
            return;
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(toEmail);
        message.setSubject("Order created");
        StringBuilder body = new StringBuilder();
        body.append("Your order has been created.\n\n");
        body.append(buildOrderSummary(order));
        if (orderUrl != null && !orderUrl.isBlank()) {
            body.append("\nPay your order: ").append(orderUrl).append('\n');
        }
        message.setText(body.toString());
        mailSender.send(message);
    }

    public void sendPaymentReceipt(Order order, Payment payment, String toEmail) {
        if (toEmail == null || toEmail.isBlank()) {
            return;
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(toEmail);
        message.setSubject("Payment receipt");
        StringBuilder body = new StringBuilder();
        body.append("We received your payment.\n\n");
        body.append(buildOrderSummary(order));
        if (payment != null && payment.getProviderPaymentId() != null) {
            body.append("\nPayment ID: ").append(payment.getProviderPaymentId()).append('\n');
        }
        message.setText(body.toString());
        mailSender.send(message);
    }

    private String buildOrderSummary(Order order) {
        StringBuilder body = new StringBuilder();
        body.append("Order ID: ").append(order.getId()).append('\n');
        body.append("Status: ").append(order.getStatus()).append('\n');
        body.append("Total: ").append(formatMoney(order.getTotalAmount())).append(" ").append(order.getTotalAmount().getCurrency()).append('\n');
        body.append("Items:\n");
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

    private String itemLabel(OrderItem item) {
        String product = item.getProductName() != null && !item.getProductName().isBlank()
                ? item.getProductName()
                : "Item";
        String variant = item.getVariantName() != null && !item.getVariantName().isBlank()
                ? " (" + item.getVariantName() + ")"
                : "";
        return product + variant;
    }

    private String formatMoney(Money money) {
        if (money == null) return "0.00";
        BigDecimal value = money.toBigDecimal();
        return value.setScale(2, RoundingMode.HALF_UP).toString();
    }
}
