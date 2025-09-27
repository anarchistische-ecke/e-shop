package ru.postel_yug.eshop.catalog.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.postel_yug.eshop.catalog.dto.CategoryDto;
import ru.postel_yug.eshop.catalog.entity.Category;
import ru.postel_yug.eshop.catalog.mapper.CategoryMapper;
import ru.postel_yug.eshop.catalog.service.CategoryService;

import java.util.List;

@RestController
@RequestMapping("/api/v1/categories")
public class CategoryController {
    private final CategoryService categoryService;
    private final CategoryMapper categoryMapper;


    public CategoryController(CategoryService categoryService, CategoryMapper categoryMapper) {
        this.categoryService = categoryService;
        this.categoryMapper = categoryMapper;
    }

    @GetMapping
    public List<CategoryDto> getAllCategories() {
        List<Category> categories = categoryService.getAllCategories();
        return categoryMapper.toDtoList(categories);
    }

    @GetMapping("/{slug}")
    public CategoryDto getCategoryBySlug(@PathVariable String slug) {
        Category category = categoryService.getCategoryBySlug(slug);
        return categoryMapper.toDto(category);
    }
}

