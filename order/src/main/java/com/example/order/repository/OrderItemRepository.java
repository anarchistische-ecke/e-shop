package com.example.order.repository;

import com.example.order.domain.OrderItem;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface OrderItemRepository extends JpaRepository<OrderItem, UUID> {

    @Query("""
            select oi.variantId as variantId, sum(oi.quantity) as totalQuantity
            from OrderItem oi
            group by oi.variantId
            order by sum(oi.quantity) desc
            """)
    List<VariantSalesView> findTopVariantSales(Pageable pageable);

    interface VariantSalesView {
        UUID getVariantId();

        Long getTotalQuantity();
    }
}
