package ru.postel_yug.eshop.notification.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import ru.postel_yug.eshop.order.entity.Order;
import ru.postel_yug.eshop.security.entity.User;

@Service
public class NotificationService {
    @Autowired
    private JavaMailSender mailSender;
    @Value("${app.notify.from:no-reply@myshop.com}")
    private String mailFrom;

    @Async
    public void sendOrderConfirmation(Order order) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(mailFrom);
        message.setTo(order.getUser().getEmail());
        message.setSubject("Order #" + order.getId() + " Confirmation");
        String text = "Здравствуйте, " + order.getUser().getName() + "!\n"
                + "Ваш заказ №" + order.getId() + " успешно оформлен.\n"
                + "Сумма: " + order.getTotalPrice().add(order.getShippingPrice()).subtract(order.getTotalDiscount()) + " руб.\n"
                + "Статус: " + order.getStatus() + "\n"
                + (order.getShippingTracking() != null ? "Трек-номер: " + order.getShippingTracking() + "\n" : "")
                + "Спасибо за покупку!";
        message.setText(text);
        try {
            mailSender.send(message);
        } catch(Exception e) {
            // Логируем ошибку, но не кидаем дальше - уведомление не должно ломать основной процесс
            System.err.println("Не удалось отправить email: " + e.getMessage());
        }
    }

    public void sendOrderStatusUpdate(Order order) {
        // Похоже на sendOrderConfirmation, можно шаблонизировать
        // В зависимости от статуса, разные тексты.
        // Например, если order.status == DELIVERED, сообщить о доставке.
    }

    public void sendPasswordReset(User user, String resetLink) {
        // Метод для отправки письма восстановления пароля, если будем реализовывать.
    }
}

