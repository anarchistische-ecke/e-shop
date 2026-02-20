package com.example.order.repository;

import com.example.order.domain.OrderCheckoutAttempt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface OrderCheckoutAttemptRepository extends JpaRepository<OrderCheckoutAttempt, UUID> {
    Optional<OrderCheckoutAttempt> findByKeyValue(String keyValue);

    long deleteByKeyValueAndOrderIdIsNull(String keyValue);
}
