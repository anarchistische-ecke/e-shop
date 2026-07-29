package com.example.api.marketing;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface MarketingSubscriptionRepository extends JpaRepository<MarketingSubscription, UUID> {
    Optional<MarketingSubscription> findByEmail(String email);
    Optional<MarketingSubscription> findByConfirmationTokenHash(String confirmationTokenHash);
    Optional<MarketingSubscription> findByUnsubscribeTokenHash(String unsubscribeTokenHash);
}
