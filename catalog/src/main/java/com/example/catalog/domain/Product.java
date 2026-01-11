package com.example.catalog.domain;

import com.example.common.domain.BaseEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;

import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "product")
public class Product extends BaseEntity {

    @NotBlank
    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "specifications", columnDefinition = "TEXT")
    private String specifications;

    @NotBlank
    @Column(name = "slug", unique = true, nullable = false)
    private String slug;

    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

    @OneToMany(mappedBy = "product", cascade = jakarta.persistence.CascadeType.ALL,
            orphanRemoval = true, fetch = FetchType.LAZY)
    private Set<ProductVariant> variants = new HashSet<>();

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "product_category",
            joinColumns = @JoinColumn(name = "product_id", columnDefinition = "uuid"),
            inverseJoinColumns = @JoinColumn(name = "category_id", columnDefinition = "uuid")
    )
    private Set<Category> categories = new HashSet<>();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "brand_id", columnDefinition = "uuid")
    private Brand brand;

    @OneToMany(mappedBy = "product", cascade = jakarta.persistence.CascadeType.ALL,
            orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("position ASC, createdAt DESC")
    private Set<ProductImage> images = new HashSet<>();

    public Product() {

    }

    public Product(String name, String description, String slug) {
        this.name = name;
        this.description = description;
        this.slug = slug;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getSlug() {
        return slug;
    }

    public void setSlug(String slug) {
        this.slug = slug;
    }

    public boolean isIsActive() {
        return isActive;
    }

    public void setIsActive(boolean isActive) {
        this.isActive = isActive;
    }

    public String getSpecifications() {
        return specifications;
    }

    public void setSpecifications(String specifications) {
        this.specifications = specifications;
    }

    public Set<ProductVariant> getVariants() {
        return variants;
    }

    public void setVariants(Set<ProductVariant> variants) {
        this.variants = variants;
    }

    public Set<Category> getCategories() {
        return categories;
    }

    public void setCategories(Set<Category> categories) {
        this.categories = categories != null ? categories : new HashSet<>();
    }

    public Brand getBrand() {
        return brand;
    }

    public void setBrand(Brand brand) {
        this.brand = brand;
    }

    public Set<ProductImage> getImages() {
        return images;
    }

    public void setImages(Set<ProductImage> images) {
        this.images = images;
    }

    public void addVariant(ProductVariant variant) {
        variants.add(variant);
        variant.setProduct(this);
    }

    public void removeVariant(ProductVariant variant) {
        variants.remove(variant);
        variant.setProduct(null);
    }

    public void addCategory(Category category) {
        if (category != null) {
            categories.add(category);
        }
    }

    public void removeCategory(Category category) {
        categories.remove(category);
    }

    public void addImage(ProductImage image) {
        images.add(image);
        image.setProduct(this);
    }

    public void removeImage(ProductImage image) {
        images.remove(image);
        image.setProduct(null);
    }
}
