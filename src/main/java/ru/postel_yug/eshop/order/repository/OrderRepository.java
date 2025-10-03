package ru.postel_yug.eshop.order.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.postel_yug.eshop.order.entity.Order;

import java.util.List;

public interface OrderRepository extends JpaRepository<Order, Long> {
    List<Order> findByUserId(Long userId);
}
