package com.example.customer.repository;

import com.example.customer.domain.EmailConfirmation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EmailConfirmationRepository extends JpaRepository<EmailConfirmation, UUID> {
    Optional<EmailConfirmation> findFirstByEmailIgnoreCaseOrderByCreatedAtDesc(String email);

    Optional<EmailConfirmation> findFirstByEmailIgnoreCaseAndCodeAndExpiresAtAfterAndVerifiedAtIsNullOrderByCreatedAtDesc(
            String email,
            String code,
            OffsetDateTime now
    );
}
