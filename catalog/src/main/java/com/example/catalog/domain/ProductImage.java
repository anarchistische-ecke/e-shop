package com.example.catalog.domain;

import com.example.common.domain.BaseEntity;
import jakarta.persistence.*;

@Entity
@Table(name = "product_image")
public class ProductImage extends BaseEntity {
    @ManyToOne(optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "variant_id")
    private ProductVariant variant;

    @Column(name = "url", nullable = false)
    private String url;

    @Column(name = "object_key", nullable = false, unique = true)
    private String objectKey;

    @Column(name = "position", nullable = false)
    private int position = 0;

    protected ProductImage() {
    }

    public ProductImage(Product product, String url, String objectKey, int position) {
        this.product = product;
        this.url = url;
        this.objectKey = objectKey;
        this.position = position;
    }

    public ProductImage(Product product, String url, String objectKey) {
        this(product, url, objectKey, 0);
    }

    public Product getProduct() {
        return product;
    }

    public void setProduct(Product product) {
        this.product = product;
    }

    public ProductVariant getVariant() {
        return variant;
    }

    public void setVariant(ProductVariant variant) {
        this.variant = variant;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getObjectKey() {
        return objectKey;
    }

    public void setObjectKey(String objectKey) {
        this.objectKey = objectKey;
    }

    public int getPosition() {
        return position;
    }

    public void setPosition(int position) {
        this.position = position;
    }
}
