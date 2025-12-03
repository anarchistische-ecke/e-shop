package com.example.catalog.domain;

import com.example.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;

@Entity
@Table(name = "stock_adjustment")
public class StockAdjustment extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "variant_id", nullable = false, columnDefinition = "uuid")
    private ProductVariant variant;

    @Column(name = "delta_quantity", nullable = false)
    private int deltaQuantity;

    @Column(name = "stock_after", nullable = false)
    private int stockAfter;

    @NotBlank
    @Column(name = "idempotency_key", nullable = false, unique = true)
    private String idempotencyKey;

    @Column(name = "reason")
    private String reason;

    public StockAdjustment() {
    }

    public StockAdjustment(ProductVariant variant, int deltaQuantity, int stockAfter, String idempotencyKey, String reason) {
        this.variant = variant;
        this.deltaQuantity = deltaQuantity;
        this.stockAfter = stockAfter;
        this.idempotencyKey = idempotencyKey;
        this.reason = reason;
    }

    public ProductVariant getVariant() {
        return variant;
    }

    public void setVariant(ProductVariant variant) {
        this.variant = variant;
    }

    public int getDeltaQuantity() {
        return deltaQuantity;
    }

    public void setDeltaQuantity(int deltaQuantity) {
        this.deltaQuantity = deltaQuantity;
    }

    public int getStockAfter() {
        return stockAfter;
    }

    public void setStockAfter(int stockAfter) {
        this.stockAfter = stockAfter;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
