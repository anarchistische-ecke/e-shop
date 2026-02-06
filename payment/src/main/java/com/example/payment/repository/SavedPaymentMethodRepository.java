package com.example.payment.repository;

import com.example.payment.domain.SavedPaymentMethod;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SavedPaymentMethodRepository extends JpaRepository<SavedPaymentMethod, UUID> {
    Optional<SavedPaymentMethod> findByProviderPaymentMethodId(String providerPaymentMethodId);

    List<SavedPaymentMethod> findByCustomerId(UUID customerId);
}
