package ru.postel_yug.eshop.catalog.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "product")
public class Product {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @NotBlank
    @Size(max = 150)
    @Column(unique = true)
    private String name;

    @NotBlank
    @Size(max = 150)
    @Column(unique = true)
    private String slug;

    @Size(max = 500)
    @Column(name = "description")
    private String description;

    @NotNull
    @ManyToOne(optional = false)
    @JoinColumn(name = "category_id")
    private Category category;

    @ManyToOne(optional = true)
    @JoinColumn(name = "brand_id")
    private Brand brand;

    @Column(name = "is_active")
    private boolean isActive = true;

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ProductVariant> variants = new ArrayList<>();

    public Product() {
    }

    public Product(String name, String slug, String description, Category category, Brand brand, boolean isActive) {
        this.name = name;
        this.slug = slug;
        this.description = description;
        this.category = category;
        this.brand = brand;
        this.isActive = isActive;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public @NotBlank @Size(max = 150) String getName() {
        return name;
    }

    public void setName(@NotBlank @Size(max = 150) String name) {
        this.name = name;
    }

    public @NotBlank @Size(max = 150) String getSlug() {
        return slug;
    }

    public void setSlug(@NotBlank @Size(max = 150) String slug) {
        this.slug = slug;
    }

    public @Size(max = 500) String getDescription() {
        return description;
    }

    public void setDescription(@Size(max = 500) String description) {
        this.description = description;
    }

    public @NotNull Category getCategory() {
        return category;
    }

    public void setCategory(@NotNull Category category) {
        this.category = category;
    }

    public Brand getBrand() {
        return brand;
    }

    public void setBrand(Brand brand) {
        this.brand = brand;
    }

    public boolean isActive() {
        return isActive;
    }

    public void setActive(boolean active) {
        isActive = active;
    }

    public List<ProductVariant> getVariants() {
        return variants;
    }

    public void setVariants(List<ProductVariant> variants) {
        this.variants = variants;
    }
}
