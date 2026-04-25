package com.example.order.domain;

import com.example.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "rma_request",
        uniqueConstraints = @UniqueConstraint(name = "uk_rma_request_rma_number", columnNames = "rma_number")
)
public class RmaRequest extends BaseEntity {
    @Column(name = "rma_number", nullable = false, unique = true)
    private String rmaNumber;

    @NotNull
    @Column(name = "order_id", nullable = false, columnDefinition = "uuid")
    private UUID orderId;

    @Column(name = "customer_email")
    private String customerEmail;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private RmaStatus status = RmaStatus.REQUESTED;

    @Column(name = "reason", columnDefinition = "TEXT")
    private String reason;

    @Column(name = "desired_resolution")
    private String desiredResolution;

    @Column(name = "manager_comment", columnDefinition = "TEXT")
    private String managerComment;

    @Column(name = "decided_by")
    private String decidedBy;

    @Column(name = "decided_at", columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private OffsetDateTime decidedAt;

    @Column(name = "decision_version", nullable = false)
    private int decisionVersion;

    public String getRmaNumber() {
        return rmaNumber;
    }

    public void setRmaNumber(String rmaNumber) {
        this.rmaNumber = rmaNumber;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public void setOrderId(UUID orderId) {
        this.orderId = orderId;
    }

    public String getCustomerEmail() {
        return customerEmail;
    }

    public void setCustomerEmail(String customerEmail) {
        this.customerEmail = customerEmail;
    }

    public RmaStatus getStatus() {
        return status;
    }

    public void setStatus(RmaStatus status) {
        this.status = status;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getDesiredResolution() {
        return desiredResolution;
    }

    public void setDesiredResolution(String desiredResolution) {
        this.desiredResolution = desiredResolution;
    }

    public String getManagerComment() {
        return managerComment;
    }

    public void setManagerComment(String managerComment) {
        this.managerComment = managerComment;
    }

    public String getDecidedBy() {
        return decidedBy;
    }

    public void setDecidedBy(String decidedBy) {
        this.decidedBy = decidedBy;
    }

    public OffsetDateTime getDecidedAt() {
        return decidedAt;
    }

    public void setDecidedAt(OffsetDateTime decidedAt) {
        this.decidedAt = decidedAt;
    }

    public int getDecisionVersion() {
        return decisionVersion;
    }

    public void setDecisionVersion(int decisionVersion) {
        this.decisionVersion = decisionVersion;
    }
}
