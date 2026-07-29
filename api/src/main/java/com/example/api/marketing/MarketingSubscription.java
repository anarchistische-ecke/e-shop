package com.example.api.marketing;

import com.example.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.OffsetDateTime;

@Entity
@Table(
        name = "marketing_subscription",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_marketing_subscription_email", columnNames = "email"),
                @UniqueConstraint(name = "uk_marketing_subscription_confirmation_token", columnNames = "confirmation_token_hash"),
                @UniqueConstraint(name = "uk_marketing_subscription_unsubscribe_token", columnNames = "unsubscribe_token_hash")
        }
)
public class MarketingSubscription extends BaseEntity {

    @Column(name = "email", nullable = false)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private MarketingSubscriptionStatus status = MarketingSubscriptionStatus.PENDING;

    @Column(name = "consent_version", nullable = false)
    private String consentVersion;

    @Column(name = "source")
    private String source;

    @Column(name = "confirmation_token_hash")
    private String confirmationTokenHash;

    @Column(name = "unsubscribe_token_hash")
    private String unsubscribeTokenHash;

    @Column(name = "requested_at", columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private OffsetDateTime requestedAt;

    @Column(name = "confirmation_expires_at", columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private OffsetDateTime confirmationExpiresAt;

    @Column(name = "confirmed_at", columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private OffsetDateTime confirmedAt;

    @Column(name = "unsubscribed_at", columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private OffsetDateTime unsubscribedAt;

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public MarketingSubscriptionStatus getStatus() {
        return status;
    }

    public void setStatus(MarketingSubscriptionStatus status) {
        this.status = status;
    }

    public String getConsentVersion() {
        return consentVersion;
    }

    public void setConsentVersion(String consentVersion) {
        this.consentVersion = consentVersion;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getConfirmationTokenHash() {
        return confirmationTokenHash;
    }

    public void setConfirmationTokenHash(String confirmationTokenHash) {
        this.confirmationTokenHash = confirmationTokenHash;
    }

    public String getUnsubscribeTokenHash() {
        return unsubscribeTokenHash;
    }

    public void setUnsubscribeTokenHash(String unsubscribeTokenHash) {
        this.unsubscribeTokenHash = unsubscribeTokenHash;
    }

    public OffsetDateTime getRequestedAt() {
        return requestedAt;
    }

    public void setRequestedAt(OffsetDateTime requestedAt) {
        this.requestedAt = requestedAt;
    }

    public OffsetDateTime getConfirmationExpiresAt() {
        return confirmationExpiresAt;
    }

    public void setConfirmationExpiresAt(OffsetDateTime confirmationExpiresAt) {
        this.confirmationExpiresAt = confirmationExpiresAt;
    }

    public OffsetDateTime getConfirmedAt() {
        return confirmedAt;
    }

    public void setConfirmedAt(OffsetDateTime confirmedAt) {
        this.confirmedAt = confirmedAt;
    }

    public OffsetDateTime getUnsubscribedAt() {
        return unsubscribedAt;
    }

    public void setUnsubscribedAt(OffsetDateTime unsubscribedAt) {
        this.unsubscribedAt = unsubscribedAt;
    }
}
