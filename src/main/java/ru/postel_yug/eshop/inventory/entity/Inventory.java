package ru.postel_yug.eshop.inventory.entity;

import jakarta.persistence.*;
import ru.postel_yug.eshop.catalog.entity.ProductVariant;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "inventory")
public class Inventory {
    @Id
    @Column(name = "variant_id", nullable = false, unique = true)
    private UUID id;

    @OneToOne(optional = false)
    @MapsId
    @JoinColumn(name = "variant_id")
    private ProductVariant productVariant;

    @Column(nullable = false)
    private Integer qty;

    @Column(nullable = false)
    private Integer reservations;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public Inventory(UUID id, ProductVariant productVariant, Integer qty, Integer reservations, LocalDateTime updatedAt) {
        this.id = id;
        this.productVariant = productVariant;
        this.qty = qty;
        this.reservations = reservations;
        this.updatedAt = updatedAt;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public ProductVariant getProductVariant() {
        return productVariant;
    }

    public void setProductVariant(ProductVariant productVariant) {
        this.productVariant = productVariant;
    }

    public Integer getQty() {
        return qty;
    }

    public void setQty(Integer qty) {
        this.qty = qty;
    }

    public Integer getReservations() {
        return reservations;
    }

    public void setReservations(Integer reservations) {
        this.reservations = reservations;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
