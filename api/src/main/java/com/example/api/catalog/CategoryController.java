package com.example.api.catalog;

import com.example.api.catalog.dto.CategoryDto;
import com.example.api.catalog.mapper.CategoryMapper;
import com.example.catalog.domain.Category;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/categories")
public class CategoryController {

    @Autowired
    private CategoryService categoryService;

    @Autowired
    private CategoryMapper categoryMapper;

    @GetMapping
    public ResponseEntity<List<CategoryDto>> listCategories() {
        List<CategoryDto> dtos = categoryService.listAll().stream()
                .map(categoryMapper::toDto)
                .collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    @GetMapping("/{slug}")
    public ResponseEntity<CategoryDto> getCategory(@PathVariable String slug) {
        Category category = categoryService.getBySlug(slug)
                .orElseThrow(() -> new IllegalArgumentException("Category not found: " + slug));
        return ResponseEntity.ok(categoryMapper.toDto(category));
    }
}
