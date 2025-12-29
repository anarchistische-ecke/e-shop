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
    public ResponseEntity<List<CategoryResponse>> listCategories() {
        List<CategoryResponse> categories = catalogService.listAllInCategory().stream()
                .map(CategoryController::toResponse)
                .toList();
        return ResponseEntity.ok(categories);
    }

    @GetMapping("/{slug}")
    public ResponseEntity<CategoryResponse> getCategoryBySlug(@PathVariable String slug) {
        Category category = catalogService.getBySlug(slug)
                .orElseThrow(() -> new IllegalArgumentException("Category not found: " + slug));
        return ResponseEntity.ok(toResponse(category));
    }

    @GetMapping("/id/{id}")
    public ResponseEntity<CategoryResponse> getCategoryById(@PathVariable UUID id) {
        Category category = catalogService.getByCategoryId(id)
                .orElseThrow(() -> new IllegalArgumentException("Category not found: " + id));
        return ResponseEntity.ok(toResponse(category));
    }

    @PostMapping
    public ResponseEntity<CategoryResponse> createCategory(@Valid @RequestBody CategoryRequest request) {
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
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(created));
    }

    @PutMapping("/{id}")
    public ResponseEntity<CategoryResponse> updateCategory(
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
        return ResponseEntity.ok(toResponse(updated));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCategory(@PathVariable UUID id) {
        catalogService.deleteCategory(id);
        return ResponseEntity.noContent().build();
    }

    private static CategoryResponse toResponse(Category category) {
        UUID parentId = category.getParent() != null ? category.getParent().getId() : null;
        return new CategoryResponse(
                category.getId(),
                category.getName(),
                category.getSlug(),
                category.getDescription(),
                parentId,
                category.getPosition(),
                category.isIsActive(),
                category.getFullPath()
        );
    }

    public static class CategoryResponse {
        private final UUID id;
        private final String name;
        private final String slug;
        private final String description;
        private final UUID parentId;
        private final int position;
        private final boolean isActive;
        private final String fullPath;

        public CategoryResponse(
                UUID id,
                String name,
                String slug,
                String description,
                UUID parentId,
                int position,
                boolean isActive,
                String fullPath
        ) {
            this.id = id;
            this.name = name;
            this.slug = slug;
            this.description = description;
            this.parentId = parentId;
            this.position = position;
            this.isActive = isActive;
            this.fullPath = fullPath;
        }

        public UUID getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public String getSlug() {
            return slug;
        }

        public String getDescription() {
            return description;
        }

        public UUID getParentId() {
            return parentId;
        }

        public int getPosition() {
            return position;
        }

        public boolean getIsActive() {
            return isActive;
        }

        public String getFullPath() {
            return fullPath;
        }
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
