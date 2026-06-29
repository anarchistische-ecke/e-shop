package com.example.order.repository;

import com.example.order.domain.RmaRequest;
import com.example.order.domain.RmaStatus;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RmaRequestRepository extends JpaRepository<RmaRequest, UUID> {
    boolean existsByRmaNumber(String rmaNumber);

    @Override
    @EntityGraph(attributePaths = "items")
    Optional<RmaRequest> findById(UUID id);

    @EntityGraph(attributePaths = "items")
    List<RmaRequest> findByOrderIdOrderByCreatedAtDesc(UUID orderId);

    @EntityGraph(attributePaths = "items")
    List<RmaRequest> findByStatusOrderByCreatedAtDesc(RmaStatus status);

    @Override
    @EntityGraph(attributePaths = "items")
    List<RmaRequest> findAll(Sort sort);

    default List<RmaRequest> findAllNewestFirst() {
        return findAll(Sort.by(Sort.Direction.DESC, "createdAt"));
    }
}
