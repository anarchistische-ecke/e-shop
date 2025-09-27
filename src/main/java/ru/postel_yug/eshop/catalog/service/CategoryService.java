package ru.postel_yug.eshop.catalog.service;

import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import ru.postel_yug.eshop.catalog.entity.Category;
import ru.postel_yug.eshop.catalog.repository.CategoryRepository;

import java.util.List;
import java.util.UUID;

@Service
public class CategoryService {
    private CategoryRepository categoryRepository;

    public CategoryService(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    public List<Category> getAllCategories() {
        return categoryRepository.findAll();
    }

    public Category getCategoryBySlug(String slug) {
        return categoryRepository.findBySlug(slug)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Category with slug '" + slug + "' is not found"));
    }

    public Category getCategoryById(UUID id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException ("Category not found"));
    }
}
