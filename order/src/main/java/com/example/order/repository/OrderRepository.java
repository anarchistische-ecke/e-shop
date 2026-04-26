package com.example.order.repository;

import com.example.order.domain.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OrderRepository extends JpaRepository<Order, UUID> {
    List<Order> findByCustomerId(UUID customerId);

    @EntityGraph(attributePaths = "items")
    Optional<Order> findByPublicToken(String publicToken);

    @EntityGraph(attributePaths = "items")
    Optional<Order> findWithItemsById(UUID id);

    @EntityGraph(attributePaths = "items")
    List<Order> findAllByOrderByOrderDateDesc();

    List<Order> findByManagerSubject(String managerSubject);

    Page<Order> findByManagerSubject(String managerSubject, Pageable pageable);

    Optional<Order> findTopByManagerSubjectOrderByOrderDateDesc(String managerSubject);

    long countByManagerSubject(String managerSubject);

    long countByManagerSubjectAndStatus(String managerSubject, String status);

    @Query("select coalesce(sum(o.totalAmount.amount), 0) from Order o where o.managerSubject = :managerSubject")
    Long sumTotalAmountByManagerSubject(@Param("managerSubject") String managerSubject);

    @Query("select coalesce(sum(o.totalAmount.amount), 0) from Order o where o.managerSubject = :managerSubject and o.status = :status")
    Long sumTotalAmountByManagerSubjectAndStatus(@Param("managerSubject") String managerSubject,
                                                 @Param("status") String status);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE customer_order
            SET manager_subject = :managerSubject,
                manager_email = :managerEmail,
                manager_directus_user_id = :managerDirectusUserId,
                manager_claimed_at = :managerClaimedAt
            WHERE id = :id
              AND (manager_subject IS NULL OR btrim(manager_subject) = '')
              AND (manager_email IS NULL OR btrim(manager_email) = '')
              AND (manager_directus_user_id IS NULL OR btrim(manager_directus_user_id) = '')
            """, nativeQuery = true)
    int claimDirectusManager(@Param("id") UUID id,
                             @Param("managerSubject") String managerSubject,
                             @Param("managerEmail") String managerEmail,
                             @Param("managerDirectusUserId") String managerDirectusUserId,
                             @Param("managerClaimedAt") OffsetDateTime managerClaimedAt);
}
