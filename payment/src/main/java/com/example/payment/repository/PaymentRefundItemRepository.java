package com.example.payment.repository;

import com.example.payment.domain.PaymentRefundItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentRefundItemRepository extends JpaRepository<PaymentRefundItem, UUID> {
    List<PaymentRefundItem> findByPaymentIdOrderByCreatedAtDesc(UUID paymentId);

    Optional<PaymentRefundItem> findByRefundIdAndOrderItemId(String refundId, UUID orderItemId);

    @Query("select coalesce(sum(i.quantity), 0) from PaymentRefundItem i where i.paymentId = :paymentId and i.orderItemId = :orderItemId and i.refundStatus = :status")
    Long sumQuantityByPaymentIdAndOrderItemIdAndStatus(@Param("paymentId") UUID paymentId,
                                                       @Param("orderItemId") UUID orderItemId,
                                                       @Param("status") String status);

    @Query("select coalesce(sum(i.refundAmount.amount), 0) from PaymentRefundItem i where i.paymentId = :paymentId and i.orderItemId = :orderItemId and i.refundStatus = :status")
    Long sumAmountByPaymentIdAndOrderItemIdAndStatus(@Param("paymentId") UUID paymentId,
                                                     @Param("orderItemId") UUID orderItemId,
                                                     @Param("status") String status);

    @Modifying
    @Query("update PaymentRefundItem i set i.refundStatus = :status where i.refundId = :refundId")
    int updateRefundStatusByRefundId(@Param("refundId") String refundId,
                                     @Param("status") String status);
}
