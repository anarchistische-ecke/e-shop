package com.example.api.admincms;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PromotionRepository extends JpaRepository<Promotion, UUID> {
    List<Promotion> findByStatusIgnoreCase(String status);
}
