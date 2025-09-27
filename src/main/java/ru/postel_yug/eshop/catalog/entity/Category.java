package ru.postel_yug.eshop.catalog.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;


@Entity
@Table(name = "category")
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @NotBlank
    @Size(max = 100)
    private String name;

    @NotBlank
    @Size(max = 100)
    @Column(unique = true)
    private String slug;

    @Size(max = 255)
    private String description;

    @Column(name = "is_active")
    private boolean isActive = true;

    public Category() {
    }

    public Category(String name, String slug, boolean isActive) {
        this.name = name;
        this.slug = slug;
        this.isActive = isActive;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public @NotBlank @Size(max = 100) String getName() {
        return name;
    }

    public void setName(@NotBlank @Size(max = 100) String name) {
        this.name = name;
    }

    public @NotBlank @Size(max = 100) String getSlug() {
        return slug;
    }

    public void setSlug(@NotBlank @Size(max = 100) String slug) {
        this.slug = slug;
    }

    public @Size(max = 256) String getDescription() {
        return description;
    }

    public void setDescription(@Size(max = 256) String description) {
        this.description = description;
    }

    public boolean isActive() {
        return isActive;
    }

    public void setActive(boolean active) {
        isActive = active;
    }
}
