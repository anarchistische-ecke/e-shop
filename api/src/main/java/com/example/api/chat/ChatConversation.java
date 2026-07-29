package com.example.api.chat;

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
        name = "chat_conversation",
        uniqueConstraints = @UniqueConstraint(name = "uk_chat_conversation_token_hash", columnNames = "customer_token_hash")
)
public class ChatConversation extends BaseEntity {
    @Column(name = "customer_token_hash", nullable = false, unique = true)
    private String customerTokenHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ChatConversationStatus status = ChatConversationStatus.OPEN;

    @Column(name = "customer_name")
    private String customerName;

    @Column(name = "customer_contact")
    private String customerContact;

    @Column(name = "customer_subject")
    private String customerSubject;

    @Column(name = "customer_email")
    private String customerEmail;

    @Column(name = "page_url")
    private String pageUrl;

    @Column(name = "user_agent")
    private String userAgent;

    @Column(name = "last_message_at", columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private OffsetDateTime lastMessageAt;

    @Column(name = "closed_at", columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private OffsetDateTime closedAt;

    public String getCustomerTokenHash() {
        return customerTokenHash;
    }

    public void setCustomerTokenHash(String customerTokenHash) {
        this.customerTokenHash = customerTokenHash;
    }

    public ChatConversationStatus getStatus() {
        return status;
    }

    public void setStatus(ChatConversationStatus status) {
        this.status = status;
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public String getCustomerContact() {
        return customerContact;
    }

    public void setCustomerContact(String customerContact) {
        this.customerContact = customerContact;
    }

    public String getCustomerSubject() {
        return customerSubject;
    }

    public void setCustomerSubject(String customerSubject) {
        this.customerSubject = customerSubject;
    }

    public String getCustomerEmail() {
        return customerEmail;
    }

    public void setCustomerEmail(String customerEmail) {
        this.customerEmail = customerEmail;
    }

    public String getPageUrl() {
        return pageUrl;
    }

    public void setPageUrl(String pageUrl) {
        this.pageUrl = pageUrl;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    public OffsetDateTime getLastMessageAt() {
        return lastMessageAt;
    }

    public void setLastMessageAt(OffsetDateTime lastMessageAt) {
        this.lastMessageAt = lastMessageAt;
    }

    public OffsetDateTime getClosedAt() {
        return closedAt;
    }

    public void setClosedAt(OffsetDateTime closedAt) {
        this.closedAt = closedAt;
    }
}
