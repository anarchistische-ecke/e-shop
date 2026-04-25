package com.example.order.domain;

import com.example.common.domain.BaseEntity;
import com.example.common.domain.Money;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

@Entity
@Table(name = "order_item")
public class OrderItem extends BaseEntity {
    @NotNull
    @Column(name = "variant_id", nullable = false, columnDefinition = "uuid")
    private UUID variantId;

    @Min(1)
    @Column(name = "quantity", nullable = false)
    private int quantity;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "amount", column = @Column(name = "unit_price_amount", nullable = false)),
            @AttributeOverride(name = "currency", column = @Column(name = "unit_price_currency", nullable = false, length = 3))
    })
    @NotNull
    private Money unitPrice;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "amount", column = @Column(name = "original_unit_price_amount")),
            @AttributeOverride(name = "currency", column = @Column(name = "original_unit_price_currency", length = 3))
    })
    private Money originalUnitPrice;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "amount", column = @Column(name = "product_sale_discount_amount")),
            @AttributeOverride(name = "currency", column = @Column(name = "product_sale_discount_currency", length = 3))
    })
    private Money productSaleDiscount;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "amount", column = @Column(name = "cart_discount_amount")),
            @AttributeOverride(name = "currency", column = @Column(name = "cart_discount_currency", length = 3))
    })
    private Money cartDiscount;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "amount", column = @Column(name = "payable_amount")),
            @AttributeOverride(name = "currency", column = @Column(name = "payable_currency", length = 3))
    })
    private Money payableAmount;

    @Column(name = "sale_promotion_id", columnDefinition = "uuid")
    private UUID salePromotionId;

    @Column(name = "sale_promotion_name")
    private String salePromotionName;

    @Column(name = "product_name")
    private String productName;

    @Column(name = "variant_name")
    private String variantName;

    @Column(name = "sku")
    private String sku;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false, columnDefinition = "uuid")
    @JsonIgnore
    private Order order;

    public OrderItem() {
    }

    public OrderItem(UUID variantId, int quantity, Money unitPrice) {
        this.variantId = variantId;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
    }

    public UUID getVariantId() {
        return variantId;
    }

    public void setVariantId(UUID variantId) {
        this.variantId = variantId;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public Money getUnitPrice() {
        return unitPrice;
    }

    public void setUnitPrice(Money unitPrice) {
        this.unitPrice = unitPrice;
    }

    public Money getOriginalUnitPrice() {
        return originalUnitPrice;
    }

    public void setOriginalUnitPrice(Money originalUnitPrice) {
        this.originalUnitPrice = originalUnitPrice;
    }

    public Money getProductSaleDiscount() {
        return productSaleDiscount;
    }

    public void setProductSaleDiscount(Money productSaleDiscount) {
        this.productSaleDiscount = productSaleDiscount;
    }

    public Money getCartDiscount() {
        return cartDiscount;
    }

    public void setCartDiscount(Money cartDiscount) {
        this.cartDiscount = cartDiscount;
    }

    public Money getPayableAmount() {
        return payableAmount;
    }

    public void setPayableAmount(Money payableAmount) {
        this.payableAmount = payableAmount;
    }

    public UUID getSalePromotionId() {
        return salePromotionId;
    }

    public void setSalePromotionId(UUID salePromotionId) {
        this.salePromotionId = salePromotionId;
    }

    public String getSalePromotionName() {
        return salePromotionName;
    }

    public void setSalePromotionName(String salePromotionName) {
        this.salePromotionName = salePromotionName;
    }

    public String getProductName() {
        return productName;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    public String getVariantName() {
        return variantName;
    }

    public void setVariantName(String variantName) {
        this.variantName = variantName;
    }

    public String getSku() {
        return sku;
    }

    public void setSku(String sku) {
        this.sku = sku;
    }

    public Order getOrder() {
        return order;
    }

    public void setOrder(Order order) {
        this.order = order;
    }

    public long getTotalAmount() {
        return unitPrice.getAmount() * quantity;
    }

    public long getPayableTotalAmount() {
        return payableAmount != null ? payableAmount.getAmount() : getTotalAmount();
    }
}
