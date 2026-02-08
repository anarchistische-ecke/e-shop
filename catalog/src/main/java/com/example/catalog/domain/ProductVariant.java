package com.example.catalog.domain;

import com.example.common.domain.BaseEntity;
import com.example.common.domain.Money;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Entity
@Table(name = "product_variant")
public class ProductVariant extends BaseEntity {

    @NotBlank
    @Column(name = "sku", unique = true, nullable = false)
    private String sku;

    @NotBlank
    @Column(name = "name", nullable = false)
    private String name;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "amount", column = @Column(name = "price_amount", nullable = false)),
            @AttributeOverride(name = "currency", column = @Column(name = "price_currency", nullable = false, length = 3))
    })
    @NotNull
    private Money price;

    @Column(name = "stock_quantity", nullable = false)
    private int stockQuantity;

    @Column(name = "weight_gross_g")
    private Integer weightGrossG;

    @Column(name = "length_mm")
    private Integer lengthMm;

    @Column(name = "width_mm")
    private Integer widthMm;

    @Column(name = "height_mm")
    private Integer heightMm;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false, columnDefinition = "uuid")
    private Product product;

    public ProductVariant() {

    }

    public ProductVariant(String sku, String name, Money price, int stockQuantity) {
        this.sku = sku;
        this.name = name;
        this.price = price;
        this.stockQuantity = stockQuantity;
    }

    public String getSku() {
        return sku;
    }

    public void setSku(String sku) {
        this.sku = sku;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Money getPrice() {
        return price;
    }

    public void setPrice(Money price) {
        this.price = price;
    }

    public int getStockQuantity() {
        return stockQuantity;
    }

    public void setStockQuantity(int stockQuantity) {
        this.stockQuantity = stockQuantity;
    }

    public Integer getWeightGrossG() {
        return weightGrossG;
    }

    public void setWeightGrossG(Integer weightGrossG) {
        this.weightGrossG = weightGrossG;
    }

    public Integer getLengthMm() {
        return lengthMm;
    }

    public void setLengthMm(Integer lengthMm) {
        this.lengthMm = lengthMm;
    }

    public Integer getWidthMm() {
        return widthMm;
    }

    public void setWidthMm(Integer widthMm) {
        this.widthMm = widthMm;
    }

    public Integer getHeightMm() {
        return heightMm;
    }

    public void setHeightMm(Integer heightMm) {
        this.heightMm = heightMm;
    }

    public Product getProduct() {
        return product;
    }

    public void setProduct(Product product) {
        this.product = product;
    }
}
