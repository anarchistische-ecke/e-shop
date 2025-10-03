package ru.postel_yug.eshop.order.service;

import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import ru.postel_yug.eshop.order.entity.Order;
import ru.postel_yug.eshop.order.entity.OrderItem;
import ru.postel_yug.eshop.order.entity.OrderStatus;
import ru.postel_yug.eshop.order.repository.OrderRepository;
import ru.postel_yug.eshop.security.entity.User;
import ru.postel_yug.eshop.security.repository.UserRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
@Transactional
public class OrderService {
    @Autowired
    private OrderRepository orderRepo;
    @Autowired
    private UserRepository userRepo;  // из модуля security/customer

    public Order createOrder(User user, List<OrderItem> items, BigDecimal shippingPrice, BigDecimal discount) {
        Order order = new Order();
        order.setUser(user);
        order.setStatus(OrderStatus.NEW);
        order.setCreatedAt(LocalDateTime.now());
        BigDecimal totalItems = items.stream()
                .map(OrderItem::getTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        order.setTotalPrice(totalItems);
        order.setShippingPrice(shippingPrice);
        order.setTotalDiscount(discount);
        // Итоговая сумма = totalItems + shippingPrice - discount (при необходимости можно вычислять при каждом изменении)
        // Связываем items с заказом
        for (OrderItem item : items) {
            item.setOrder(order);
            order.getItems().add(item);
        }
        Order saved = orderRepo.save(order);
        return saved;
    }

    public List<Order> getOrdersForUser(Long userId) {
        return orderRepo.findByUserId(userId);
    }

    public void updateStatus(Long orderId, OrderStatus newStatus) {
        Order order = orderRepo.findById(orderId).orElseThrow();
        order.setStatus(newStatus);
        if(newStatus == OrderStatus.PAID) {
            order.setPaidAt(LocalDateTime.now());
        }
        if(newStatus == OrderStatus.DELIVERED) {
            order.setDeliveredAt(LocalDateTime.now());
        }
        // Сохраняем изменения статуса
        orderRepo.save(order);
    }
    // ... другие методы: findById, maybe cancelOrder etc.
}
