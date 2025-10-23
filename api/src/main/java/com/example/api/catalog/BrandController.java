package com.example.api.catalog;

import com.example.catalog.domain.Brand;
import com.example.catalog.service.CatalogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/brands")
public class BrandController {

    private final CatalogService catalogService;

    @Autowired
    public BrandController(CatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @GetMapping
    public ResponseEntity<List<Brand>> listBrands() {
        List<Brand> brands = catalogService.listAllInBrand();
        return ResponseEntity.ok(brands);
    }

    @GetMapping("/{slug}")
    public ResponseEntity<Brand> getBrandBySlug(@PathVariable String slug) {
        Brand brand = catalogService.getByBrandSlug(slug).orElseThrow(() ->
                new IllegalArgumentException("Brand not found: " + slug));
        return ResponseEntity.ok(brand);
    }

    @GetMapping("/id/{id}")
    public ResponseEntity<Brand> getBrandById(@PathVariable UUID id) {
        Brand brand = catalogService.getById(id).orElseThrow(() ->
                new IllegalArgumentException("Brand not found: " + id));
        return ResponseEntity.ok(brand);
    }

    @PostMapping
    public ResponseEntity<Brand> createBrand(@Valid @RequestBody BrandRequest request) {
        Brand brand = new Brand(request.getName(), request.getDescription(), request.getSlug());
        Brand created = catalogService.create(brand);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Brand> updateBrand(@PathVariable UUID id, @Valid @RequestBody BrandRequest request) {
        Brand updates = new Brand(request.getName(), request.getDescription(), request.getSlug());
        Brand updated = catalogService.update(id, updates);
        return ResponseEntity.ok(updated);
    }

    public static class BrandRequest {
        @NotBlank
        private String name;
        private String description;
        @NotBlank
        private String slug;

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
    }
}

