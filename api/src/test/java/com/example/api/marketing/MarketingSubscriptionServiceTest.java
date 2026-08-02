package com.example.api.marketing;

import com.example.api.notification.NotificationService;
import com.example.api.notification.NotificationType;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MarketingSubscriptionServiceTest {

    @Test
    void signupStoresOnlyTokenHashesAndEnqueuesDurableConfirmation() {
        MarketingSubscriptionRepository repository = mock(MarketingSubscriptionRepository.class);
        NotificationService notifications = mock(NotificationService.class);
        when(repository.findByEmail("buyer@example.com")).thenReturn(Optional.empty());
        when(repository.save(any(MarketingSubscription.class))).thenAnswer(invocation -> {
            MarketingSubscription value = invocation.getArgument(0);
            value.setId(UUID.randomUUID());
            return value;
        });
        MarketingSubscriptionService service = new MarketingSubscriptionService(
                repository,
                notifications,
                "https://shop.example"
        );

        service.requestSubscription("  Buyer@Example.COM ", true, "home_newsletter");

        ArgumentCaptor<MarketingSubscription> captor =
                ArgumentCaptor.forClass(MarketingSubscription.class);
        verify(repository).save(captor.capture());
        MarketingSubscription saved = captor.getValue();
        assertThat(saved.getEmail()).isEqualTo("buyer@example.com");
        assertThat(saved.getStatus()).isEqualTo(MarketingSubscriptionStatus.PENDING);
        assertThat(saved.getConsentVersion()).isEqualTo("2026-07-v1");
        assertThat(saved.getConfirmationTokenHash()).hasSize(64);
        assertThat(saved.getUnsubscribeTokenHash()).hasSize(64);
        assertThat(saved.getConfirmationExpiresAt())
                .isAfter(saved.getRequestedAt().plusHours(23));
        verify(notifications).enqueueOnce(
                eq(NotificationType.MARKETING_CONFIRMATION),
                any(String.class),
                eq("marketing_subscription"),
                eq(saved.getId()),
                eq("buyer@example.com"),
                any()
        );
    }

    @Test
    void duplicatePendingSignupHonorsTenMinuteCooldownWithoutDisclosureWork() {
        MarketingSubscriptionRepository repository = mock(MarketingSubscriptionRepository.class);
        NotificationService notifications = mock(NotificationService.class);
        MarketingSubscription pending = new MarketingSubscription();
        pending.setEmail("buyer@example.com");
        pending.setStatus(MarketingSubscriptionStatus.PENDING);
        pending.setRequestedAt(OffsetDateTime.now().minusMinutes(2));
        when(repository.findByEmail("buyer@example.com")).thenReturn(Optional.of(pending));
        MarketingSubscriptionService service = new MarketingSubscriptionService(
                repository,
                notifications,
                "https://shop.example"
        );

        service.requestSubscription("buyer@example.com", true, "footer");

        verify(repository, never()).save(any());
        verify(notifications, never()).enqueueOnce(
                any(), any(), any(), any(), any(), any()
        );
    }

    @Test
    void confirmationHandlesExpiryAndIsIdempotentAfterActivation() {
        MarketingSubscriptionRepository repository = mock(MarketingSubscriptionRepository.class);
        NotificationService notifications = mock(NotificationService.class);
        MarketingSubscription subscription = new MarketingSubscription();
        subscription.setStatus(MarketingSubscriptionStatus.PENDING);
        subscription.setConfirmationExpiresAt(OffsetDateTime.now().minusMinutes(1));
        when(repository.findByConfirmationTokenHash(any(String.class)))
                .thenReturn(Optional.of(subscription));
        MarketingSubscriptionService service = new MarketingSubscriptionService(
                repository,
                notifications,
                "https://shop.example"
        );

        assertThat(service.confirm("expired-token"))
                .isEqualTo(MarketingSubscriptionService.TokenResult.EXPIRED);
        verify(repository, never()).save(any());

        subscription.setConfirmationExpiresAt(OffsetDateTime.now().plusHours(2));
        assertThat(service.confirm("valid-token"))
                .isEqualTo(MarketingSubscriptionService.TokenResult.CONFIRMED);
        assertThat(subscription.getStatus()).isEqualTo(MarketingSubscriptionStatus.ACTIVE);
        verify(repository).save(subscription);

        assertThat(service.confirm("valid-token"))
                .isEqualTo(MarketingSubscriptionService.TokenResult.CONFIRMED);
    }

    @Test
    void unsubscribeIsIdempotentAndEnqueuesOneConfirmationMessage() {
        MarketingSubscriptionRepository repository = mock(MarketingSubscriptionRepository.class);
        NotificationService notifications = mock(NotificationService.class);
        MarketingSubscription subscription = new MarketingSubscription();
        subscription.setId(UUID.randomUUID());
        subscription.setEmail("buyer@example.com");
        subscription.setStatus(MarketingSubscriptionStatus.ACTIVE);
        when(repository.findByUnsubscribeTokenHash(any(String.class)))
                .thenReturn(Optional.of(subscription));
        when(repository.save(subscription)).thenReturn(subscription);
        MarketingSubscriptionService service = new MarketingSubscriptionService(
                repository,
                notifications,
                "https://shop.example"
        );

        assertThat(service.unsubscribe("unsubscribe-token"))
                .isEqualTo(MarketingSubscriptionService.TokenResult.UNSUBSCRIBED);
        assertThat(service.unsubscribe("unsubscribe-token"))
                .isEqualTo(MarketingSubscriptionService.TokenResult.UNSUBSCRIBED);

        verify(repository).save(subscription);
        verify(notifications).enqueueOnce(
                eq(NotificationType.MARKETING_UNSUBSCRIBE),
                any(String.class),
                eq("marketing_subscription"),
                eq(subscription.getId()),
                eq(subscription.getEmail()),
                any()
        );
    }
}
