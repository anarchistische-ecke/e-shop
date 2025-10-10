package com.example.catalog.service;

import com.example.catalog.domain.Product;
import com.example.catalog.domain.ProductVariant;
import com.example.catalog.repository.ProductRepository;
import com.example.catalog.repository.ProductVariantRepository;
import com.example.common.domain.Money;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional
public class CatalogService {
    private final ProductRepository productRepository;
    private final ProductVariantRepository variantRepository;

    @Autowired
    public CatalogService(ProductRepository productRepository,
                          ProductVariantRepository variantRepository) {
        this.productRepository = productRepository;
        this.variantRepository = variantRepository;
    }

    public Product createProduct(String name, String description, String slug) {
        Product product = new Product(name, description, slug);
        return productRepository.save(product);
    }

    public ProductVariant addVariant(UUID productId, String sku, String name, Money price, int stock) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Product not found: " + productId));
        ProductVariant variant = new ProductVariant(sku, name, price, stock);
        product.addVariant(variant);
        // saving the product will cascade to the variant
        productRepository.save(product);
        return variant;
    }

    @Transactional
    public Optional<Product> getProduct(UUID id) {
        return productRepository.findById(id);
    }

    public List<Product> getAllProducts() {
        return productRepository.findAll();
    }
}