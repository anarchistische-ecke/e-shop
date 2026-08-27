package com.example.payment.repository;

import com.example.payment.domain.Payment;
import com.example.payment.domain.PaymentStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, UUID> {
    Optional<Payment> findByProviderPaymentId(String providerPaymentId);

    Optional<Payment> findTopByOrderIdOrderByPaymentDateDesc(UUID orderId);

    @Query("""
            select p.providerPaymentId
            from Payment p
            where p.status = :status
              and lower(p.receiptRegistration) = 'pending'
              and p.providerPaymentId is not null
              and p.providerPaymentId <> ''
            order by p.paymentDate asc
            """)
    List<String> findProviderPaymentIdsWithPendingReceipt(@Param("status") PaymentStatus status,
                                                          Pageable pageable);
}
