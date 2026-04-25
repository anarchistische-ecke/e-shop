package com.example.payment.repository;

import com.example.payment.domain.PaymentRefund;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentRefundRepository extends JpaRepository<PaymentRefund, UUID> {
    Optional<PaymentRefund> findByRefundId(String refundId);

    List<PaymentRefund> findByPaymentIdOrderByCreatedAtDesc(UUID paymentId);

    boolean existsByPaymentIdAndRefundStatus(UUID paymentId, String refundStatus);

    long countByPaymentId(UUID paymentId);

    @Query("select coalesce(sum(r.refundAmount.amount), 0) from PaymentRefund r where r.paymentId = :paymentId and r.refundStatus = :status")
    Long sumAmountByPaymentIdAndStatus(@Param("paymentId") UUID paymentId,
                                       @Param("status") String status);
}
