package ru.postel_yug.eshop.order.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.postel_yug.eshop.order.entity.OrderItem;

import java.util.List;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {
    List<OrderItem> findByOrderId(Long orderId);
}
