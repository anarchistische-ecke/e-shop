package com.example.api.admincms;

import com.example.common.domain.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "promotion")
public class Promotion extends BaseEntity {

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "type", nullable = false, length = 40)
    private String type;

    @Column(name = "status", nullable = false, length = 40)
    private String status = "DRAFT";

    @Column(name = "starts_at", columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private OffsetDateTime startsAt;

    @Column(name = "ends_at", columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private OffsetDateTime endsAt;

    @Column(name = "discount_percent")
    private Integer discountPercent;

    @Column(name = "discount_amount")
    private Long discountAmount;

    @Column(name = "sale_price_amount")
    private Long salePriceAmount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "RUB";

    @Column(name = "threshold_amount")
    private Long thresholdAmount;

    @Column(name = "description")
    private String description;

    @OneToMany(mappedBy = "promotion", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<PromotionTarget> targets = new ArrayList<>();

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public OffsetDateTime getStartsAt() {
        return startsAt;
    }

    public void setStartsAt(OffsetDateTime startsAt) {
        this.startsAt = startsAt;
    }

    public OffsetDateTime getEndsAt() {
        return endsAt;
    }

    public void setEndsAt(OffsetDateTime endsAt) {
        this.endsAt = endsAt;
    }

    public Integer getDiscountPercent() {
        return discountPercent;
    }

    public void setDiscountPercent(Integer discountPercent) {
        this.discountPercent = discountPercent;
    }

    public Long getDiscountAmount() {
        return discountAmount;
    }

    public void setDiscountAmount(Long discountAmount) {
        this.discountAmount = discountAmount;
    }

    public Long getSalePriceAmount() {
        return salePriceAmount;
    }

    public void setSalePriceAmount(Long salePriceAmount) {
        this.salePriceAmount = salePriceAmount;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public Long getThresholdAmount() {
        return thresholdAmount;
    }

    public void setThresholdAmount(Long thresholdAmount) {
        this.thresholdAmount = thresholdAmount;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public List<PromotionTarget> getTargets() {
        return targets;
    }

    public void setTargets(List<PromotionTarget> targets) {
        this.targets.clear();
        if (targets != null) {
            targets.forEach(this::addTarget);
        }
    }

    public void addTarget(PromotionTarget target) {
        if (target == null) {
            return;
        }
        targets.add(target);
        target.setPromotion(this);
    }
}
