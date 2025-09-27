package ru.postel_yug.eshop.catalog.service;

import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import ru.postel_yug.eshop.catalog.entity.Product;
import ru.postel_yug.eshop.catalog.repository.ProductRepository;

import java.util.List;
import java.util.UUID;

@Service
public class ProductService {
    private final ProductRepository productRepository;

    public ProductService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    public List<Product> getAllProducts() {
        return productRepository.findAll();
    }

    public Product findBySlug(String slug) {
        return productRepository.findBySlug(slug)
                .orElseThrow(() -> new EntityNotFoundException("Product '" + slug + "' is not found"));
    }

    public List<Product> getProductsByCategory(UUID categoryId) {
        return productRepository.findByCategoryId(categoryId);
    }

    public List<Product> getProductsByBrand(UUID brandId) {
        return productRepository.findByBrandId(brandId);
    }
}
