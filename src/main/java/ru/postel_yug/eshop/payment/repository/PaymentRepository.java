package ru.postel_yug.eshop.payment.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.postel_yug.eshop.payment.entity.PaymentTransaction;

import java.util.Optional;

public interface PaymentRepository extends JpaRepository<PaymentTransaction, Long> {
    Optional<PaymentTransaction> findByOrderId(Long orderId);
    Optional<PaymentTransaction> findByTransactionId(String transactionId);
}

