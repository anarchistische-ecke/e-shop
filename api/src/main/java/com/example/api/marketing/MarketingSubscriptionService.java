package com.example.api.marketing;

import com.example.api.notification.NotificationService;
import com.example.api.notification.NotificationType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;

@Service
public class MarketingSubscriptionService {

    public static final String CONSENT_VERSION = "2026-07-v1";
    private static final Duration CONFIRMATION_TTL = Duration.ofHours(24);
    private static final Duration RESEND_COOLDOWN = Duration.ofMinutes(10);

    private final MarketingSubscriptionRepository repository;
    private final NotificationService notificationService;
    private final SecureRandom secureRandom = new SecureRandom();
    private final String publicBaseUrl;

    public MarketingSubscriptionService(
            MarketingSubscriptionRepository repository,
            NotificationService notificationService,
            @Value("${app.marketing.public-base-url:https://yug-postel.ru}") String publicBaseUrl
    ) {
        this.repository = repository;
        this.notificationService = notificationService;
        this.publicBaseUrl = normalizeBaseUrl(publicBaseUrl);
    }

    @Transactional
    public void requestSubscription(String email, boolean consent, String source) {
        if (!consent) {
            throw new IllegalArgumentException("Explicit consent is required");
        }
        String normalizedEmail = normalizeEmail(email);
        if (!NotificationService.isValidEmail(normalizedEmail)) {
            throw new IllegalArgumentException("Invalid email");
        }

        OffsetDateTime now = OffsetDateTime.now();
        MarketingSubscription subscription = repository.findByEmail(normalizedEmail)
                .orElseGet(MarketingSubscription::new);
        if (subscription.getStatus() == MarketingSubscriptionStatus.ACTIVE) {
            return;
        }
        if (subscription.getStatus() == MarketingSubscriptionStatus.PENDING
                && subscription.getRequestedAt() != null
                && subscription.getRequestedAt().plus(RESEND_COOLDOWN).isAfter(now)) {
            return;
        }

        String confirmationToken = token();
        String unsubscribeToken = token();
        subscription.setEmail(normalizedEmail);
        subscription.setStatus(MarketingSubscriptionStatus.PENDING);
        subscription.setConsentVersion(CONSENT_VERSION);
        subscription.setSource(normalizeSource(source));
        subscription.setConfirmationTokenHash(hash(confirmationToken));
        subscription.setUnsubscribeTokenHash(hash(unsubscribeToken));
        subscription.setRequestedAt(now);
        subscription.setConfirmationExpiresAt(now.plus(CONFIRMATION_TTL));
        subscription.setConfirmedAt(null);
        subscription.setUnsubscribedAt(null);
        MarketingSubscription saved = repository.save(subscription);

        notificationService.enqueueOnce(
                NotificationType.MARKETING_CONFIRMATION,
                "marketing-confirm:" + saved.getId() + ":" + saved.getConfirmationTokenHash(),
                "marketing_subscription",
                saved.getId(),
                saved.getEmail(),
                Map.of(
                        "confirmationUrl", link("/subscribe/confirm", confirmationToken),
                        "unsubscribeUrl", link("/subscribe/unsubscribe", unsubscribeToken),
                        "expiresHours", 24
                )
        );
    }

    @Transactional
    public TokenResult confirm(String token) {
        String tokenHash = hashNullable(token);
        if (!StringUtils.hasText(tokenHash)) {
            return TokenResult.INVALID;
        }
        return repository.findByConfirmationTokenHash(tokenHash)
                .map(subscription -> {
                    if (subscription.getStatus() == MarketingSubscriptionStatus.ACTIVE) {
                        return TokenResult.CONFIRMED;
                    }
                    OffsetDateTime now = OffsetDateTime.now();
                    if (subscription.getConfirmationExpiresAt() == null
                            || subscription.getConfirmationExpiresAt().isBefore(now)) {
                        return TokenResult.EXPIRED;
                    }
                    subscription.setStatus(MarketingSubscriptionStatus.ACTIVE);
                    subscription.setConfirmedAt(now);
                    subscription.setUnsubscribedAt(null);
                    repository.save(subscription);
                    return TokenResult.CONFIRMED;
                })
                .orElse(TokenResult.INVALID);
    }

    @Transactional
    public TokenResult unsubscribe(String token) {
        String tokenHash = hashNullable(token);
        if (!StringUtils.hasText(tokenHash)) {
            return TokenResult.INVALID;
        }
        return repository.findByUnsubscribeTokenHash(tokenHash)
                .map(subscription -> {
                    if (subscription.getStatus() == MarketingSubscriptionStatus.UNSUBSCRIBED) {
                        return TokenResult.UNSUBSCRIBED;
                    }
                    OffsetDateTime now = OffsetDateTime.now();
                    subscription.setStatus(MarketingSubscriptionStatus.UNSUBSCRIBED);
                    subscription.setUnsubscribedAt(now);
                    MarketingSubscription saved = repository.save(subscription);
                    notificationService.enqueueOnce(
                            NotificationType.MARKETING_UNSUBSCRIBE,
                            "marketing-unsubscribe:" + saved.getId() + ":" + now.toInstant().toEpochMilli(),
                            "marketing_subscription",
                            saved.getId(),
                            saved.getEmail(),
                            Map.of("resubscribeUrl", publicBaseUrl + "/#newsletter")
                    );
                    return TokenResult.UNSUBSCRIBED;
                })
                .orElse(TokenResult.INVALID);
    }

    private String token() {
        byte[] value = new byte[32];
        secureRandom.nextBytes(value);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private String hashNullable(String token) {
        return StringUtils.hasText(token) ? hash(token.trim()) : null;
    }

    private String hash(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))
            );
        } catch (Exception error) {
            throw new IllegalStateException("SHA-256 is unavailable", error);
        }
    }

    private String link(String path, String token) {
        return publicBaseUrl + path + "?token=" + URLEncoder.encode(token, StandardCharsets.UTF_8);
    }

    private String normalizeEmail(String value) {
        return StringUtils.hasText(value) ? value.trim().toLowerCase(Locale.ROOT) : "";
    }

    private String normalizeSource(String value) {
        if (!StringUtils.hasText(value)) return "storefront";
        String normalized = value.trim();
        return normalized.length() > 100 ? normalized.substring(0, 100) : normalized;
    }

    private String normalizeBaseUrl(String value) {
        String normalized = StringUtils.hasText(value) ? value.trim() : "https://yug-postel.ru";
        return normalized.replaceAll("/+$", "");
    }

    public enum TokenResult {
        CONFIRMED,
        UNSUBSCRIBED,
        EXPIRED,
        INVALID
    }
}
