package com.example.catalog.domain;

import com.example.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public class Brand extends BaseEntity {

    @NotNull
    @Column(name = "brand_id", nullable = false, columnDefinition = "uuid")
    private UUID brandId;

    @NotBlank
    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @NotBlank
    @Column(name = "slug", unique = true, nullable = false)
    private String slug;

    public Brand() {

    }

    public Brand(String name, String description, String slug) {
        this.name = name;
        this.description = description;
        this.slug = slug;
    }

    public @NotBlank String getName() {
        return name;
    }

    public void setName(@NotBlank String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public @NotBlank String getSlug() {
        return slug;
    }

    public void setSlug(@NotBlank String slug) {
        this.slug = slug;
    }
}
