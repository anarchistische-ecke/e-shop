package com.example.catalog.repository;

import com.example.catalog.domain.StockAdjustment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface StockAdjustmentRepository extends JpaRepository<StockAdjustment, UUID> {
    Optional<StockAdjustment> findByIdempotencyKey(String idempotencyKey);
}
