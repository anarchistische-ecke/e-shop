package com.example.api.catalog;


import com.example.catalog.domain.Category;
import com.example.catalog.service.CatalogService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/categories")
public class CategoryController {

    private final CatalogService catalogService;

    @Autowired
    public CategoryController(CatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @GetMapping
    public ResponseEntity<List<Category>> listCategories() {
        List<Category> categories = catalogService.listAllInCategory();
        return ResponseEntity.ok(categories);
    }

    @GetMapping("/{slug}")
    public ResponseEntity<Category> getCategoryBySlug(@PathVariable String slug) {
        Category category = catalogService.getBySlug(slug)
                .orElseThrow(() -> new IllegalArgumentException("Category not found: " + slug));
        return ResponseEntity.ok(category);
    }

    @GetMapping("/id/{id}")
    public ResponseEntity<Category> getCategoryById(@PathVariable UUID id) {
        Category category = catalogService.getByCategoryId(id)
                .orElseThrow(() -> new IllegalArgumentException("Category not found: " + id));
        return ResponseEntity.ok(category);
    }

    @PostMapping
    public ResponseEntity<Category> createCategory(@Valid @RequestBody CategoryRequest request) {
        Category parent = null;
        if (request.getParentId() != null) {
            parent = catalogService.getByCategoryId(request.getParentId())
                    .orElseThrow(() -> new IllegalArgumentException("Category not found: " + request.getParentId()));
        }
        Category category = new Category(request.getName(), request.getDescription(), request.getSlug(), parent);
        if (request.getPosition() != null) {
            category.setPosition(request.getPosition());
        }
        if (request.getIsActive() != null) {
            category.setIsActive(request.getIsActive());
        }
        Category created = catalogService.create(category);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Category> updateCategory(
            @PathVariable UUID id,
            @Valid @RequestBody CategoryRequest request) {
        Category parent = null;
        if (request.getParentId() != null) {
            parent = catalogService.getByCategoryId(request.getParentId())
                    .orElseThrow(() -> new IllegalArgumentException("Category not found: " + request.getParentId()));
        }
        Category updates = new Category(request.getName(), request.getDescription(), request.getSlug(), parent);
        if (request.getPosition() != null) {
            updates.setPosition(request.getPosition());
        }
        if (request.getIsActive() != null) {
            updates.setIsActive(request.getIsActive());
        }
        Category updated = catalogService.update(id, updates);
        return ResponseEntity.ok(updated);
    }

    public static class CategoryRequest {
        @NotBlank
        private String name;
        @NotBlank
        private String slug;
        private String description;
        private UUID parentId;
        @Min(0)
        private Integer position;
        private Boolean isActive;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getSlug() {
            return slug;
        }

        public void setSlug(String slug) {
            this.slug = slug;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public UUID getParentId() {
            return parentId;
        }

        public void setParentId(UUID parentId) {
            this.parentId = parentId;
        }

        public Integer getPosition() {
            return position;
        }

        public void setPosition(Integer position) {
            this.position = position;
        }

        public Boolean getIsActive() {
            return isActive;
        }

        public void setIsActive(Boolean isActive) {
            this.isActive = isActive;
        }
    }
}

