package ru.postel_yug.eshop.catalog.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

@Entity
@Table(name = "product_variant")
public class ProductVariant {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @NotNull
    @ManyToOne(optional = false)
    @JoinColumn(name = "product_id")
    private Product product;

    @NotBlank
    @Size(max = 50)
    @Column(name = "sku_code", unique = true)
    private String sku;

    @NotBlank
    @Size(max = 150)
    @Column(name = "variant_name")
    private String variantName;

    @Column(name = "is_active")
    private boolean isActive = true;

    public ProductVariant() {
    }

    public ProductVariant(Product product, String sku, String variantName, boolean isActive) {
        this.product = product;
        this.sku = sku;
        this.variantName = variantName;
        this.isActive = isActive;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public @NotNull Product getProduct() {
        return product;
    }

    public void setProduct(@NotNull Product product) {
        this.product = product;
    }

    public @NotBlank @Size(max = 50) String getSku() {
        return sku;
    }

    public void setSku(@NotBlank @Size(max = 50) String sku) {
        this.sku = sku;
    }

    public @NotBlank @Size(max = 150) String getVariantName() {
        return variantName;
    }

    public void setVariantName(@NotBlank @Size(max = 150) String variantName) {
        this.variantName = variantName;
    }

    public boolean isActive() {
        return isActive;
    }

    public void setActive(boolean active) {
        isActive = active;
    }
}
