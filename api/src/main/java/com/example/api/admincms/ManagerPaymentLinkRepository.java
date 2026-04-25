package com.example.api.admincms;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface ManagerPaymentLinkRepository extends JpaRepository<ManagerPaymentLink, UUID> {
    List<ManagerPaymentLink> findByCreatedAtBetweenOrderByCreatedAtDesc(OffsetDateTime from, OffsetDateTime to);
    List<ManagerPaymentLink> findTop100ByOrderByCreatedAtDesc();
}
