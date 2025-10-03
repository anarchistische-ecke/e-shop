package ru.postel_yug.eshop.order.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import ru.postel_yug.eshop.catalog.service.ProductService;
import ru.postel_yug.eshop.notification.service.NotificationService;
import ru.postel_yug.eshop.order.dto.CheckoutRequest;
import ru.postel_yug.eshop.order.dto.ItemRequest;
import ru.postel_yug.eshop.order.entity.Order;
import ru.postel_yug.eshop.order.entity.OrderItem;
import ru.postel_yug.eshop.order.entity.OrderStatus;
import ru.postel_yug.eshop.payment.service.PaymentService;
import ru.postel_yug.eshop.promo.service.PromoService;
import ru.postel_yug.eshop.security.entity.User;
import ru.postel_yug.eshop.shipping.service.ShippingService;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class CheckoutService {
    @Autowired
    private ProductService productService;      // предположим, существует или заменим получением цен
    @Autowired private OrderService orderService;
    @Autowired private PaymentService paymentService;
    @Autowired private ShippingService shippingService;
    @Autowired private PromoService promoService;
    @Autowired private NotificationService notificationService;

    public Order checkout(CheckoutRequest request, User user) {
        // 1. Получение и проверка информации о товарах
        List<OrderItem> orderItems = new ArrayList<>();
        for(ItemRequest itemReq : request.getItems()) {
            ProductInfo product = productService.getProductById(itemReq.getProductId());
            if(product == null) {
                throw new IllegalArgumentException("Товар не найден: " + itemReq.getProductId());
            }
            if(product.getStock() < itemReq.getQuantity()) {
                throw new IllegalArgumentException("Недостаточно товара на складе: " + product.getName());
            }
            // Рассчитываем стоимость позиции
            BigDecimal price = product.getPrice();
            BigDecimal total = price.multiply(BigDecimal.valueOf(itemReq.getQuantity()));
            OrderItem orderItem = new OrderItem();
            orderItem.setProductId(product.getId());
            orderItem.setProductName(product.getName());
            orderItem.setQuantity(itemReq.getQuantity());
            orderItem.setPrice(price);
            orderItem.setTotal(total);
            orderItems.add(orderItem);
        }
        // 2. Применение промокода (если есть)
        BigDecimal totalItems = orderItems.stream().map(OrderItem::getTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal discount = BigDecimal.ZERO;
        if(request.getPromoCode() != null && !request.getPromoCode().isBlank()) {
            discount = promoService.calculateDiscount(request.getPromoCode(), totalItems);
            if(discount.compareTo(BigDecimal.ZERO) > 0) {
                totalItems = totalItems.subtract(discount);
            } else {
                // невалидный или неприменимый промокод
                throw new IllegalArgumentException("Промо-код недействителен");
            }
        }
        // 3. Расчет стоимости доставки
        BigDecimal shippingCost = shippingService.calculateShippingCost(request.getDeliveryCity(), request.getDeliveryType(), totalItems);
        // 4. Создание заказа в БД (статус NEW или PENDING_PAYMENT)
        Order order = orderService.createOrder(user, orderItems, shippingCost, discount);
        order.setStatus(OrderStatus.PENDING_PAYMENT);
        // (присваиваем PENDING_PAYMENT, т.к. до оплаты)
        order = orderService.updateStatus(order.getId(), OrderStatus.PENDING_PAYMENT);
        // 5. Инициация платежа через платежный сервис
        try {
            paymentService.processPayment(order, request.getPayment());
            // Если оплата прошла:
            orderService.updateStatus(order.getId(), OrderStatus.PAID);
            order.setPaidAt(LocalDateTime.now());
        } catch (PaymentFailedException e) {
            orderService.updateStatus(order.getId(), OrderStatus.CANCELLED);
            // Можно выбросить исключение вверх, чтобы вернуть ошибку клиенту
            throw e;
        }
        // 6. Оформление доставки через CDEK
        try {
            String trackingNumber = shippingService.createShipment(order, request.getDeliveryCity(), request.getAddress());
            order.setShippingTracking(trackingNumber);
            orderService.updateStatus(order.getId(), OrderStatus.SHIPPED);
        } catch(Exception e) {
            // Если создание отправления не удалось, логируем ошибку и оставляем заказ в статусе PAID
            // (администратор сможет повторить отправку вручную)
            // Не переводим в CANCELLED, т.к. оплата прошла, товар должен быть доставлен либо возвращен.
        }
        // 7. Отправка уведомления о заказе
        notificationService.sendOrderConfirmation(order);
        return order;
    }
}

