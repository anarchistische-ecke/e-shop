package com.example.api.admincms;

import com.example.common.domain.BaseEntity;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "promotion_target")
public class PromotionTarget extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "promotion_id", nullable = false)
    @JsonIgnore
    private Promotion promotion;

    @Column(name = "target_kind", nullable = false, length = 40)
    private String targetKind;

    @Column(name = "target_key", nullable = false)
    private String targetKey;

    public Promotion getPromotion() {
        return promotion;
    }

    public void setPromotion(Promotion promotion) {
        this.promotion = promotion;
    }

    public String getTargetKind() {
        return targetKind;
    }

    public void setTargetKind(String targetKind) {
        this.targetKind = targetKind;
    }

    public String getTargetKey() {
        return targetKey;
    }

    public void setTargetKey(String targetKey) {
        this.targetKey = targetKey;
    }
}
