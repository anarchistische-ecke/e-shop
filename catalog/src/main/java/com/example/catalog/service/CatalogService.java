package com.example.catalog.service;

import com.example.catalog.domain.Brand;
import com.example.catalog.domain.Category;
import com.example.catalog.domain.Product;
import com.example.catalog.domain.ProductVariant;
import com.example.catalog.repository.BrandRepository;
import com.example.catalog.repository.CategoryRepository;
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
    private final BrandRepository brandRepository;
    private final CategoryRepository categoryRepository;

    @Autowired
    public CatalogService(ProductRepository productRepository, ProductVariantRepository variantRepository, BrandRepository brandRepository, CategoryRepository categoryRepository) {
        this.productRepository = productRepository;
        this.variantRepository = variantRepository;
        this.brandRepository = brandRepository;
        this.categoryRepository = categoryRepository;
    }

    public Product createProduct(String name, String description, String slug) {
        Product product = new Product(name, description, slug);
        return productRepository.save(product);
    }

    public ProductVariant addVariant(UUID productId, String sku, String name, Money price, int stock) {
        Product product = productRepository.findById(productId).orElseThrow(() -> new IllegalArgumentException("Product not found: " + productId));
        ProductVariant variant = new ProductVariant(sku, name, price, stock);
        product.addVariant(variant);
        // saving the product will cascade to the variant
        productRepository.save(product);
        return variant;
    }

    public List<Product> getProducts(String categorySlug, String brandSlug) {
        if (categorySlug != null && !categorySlug.isBlank() &&
                brandSlug != null && !brandSlug.isBlank()) {
            return productRepository.findByCategory_SlugAndBrand_Slug(categorySlug, brandSlug);
        }
        if (categorySlug != null && !categorySlug.isBlank()) {
            return productRepository.findByCategory_Slug(categorySlug);
        }
        if (brandSlug != null && !brandSlug.isBlank()) {
            return productRepository.findByBrand_Slug(brandSlug);
        }
        return productRepository.findAll();
    }

    @Transactional
    public Optional<Product> getProduct(UUID id) {
        return productRepository.findById(id);
    }

    public List<Product> getAllProducts() {
        return productRepository.findAll();
    }

    @Transactional
    public Product updateProduct(UUID id, Product updates) {
        return productRepository.findById(id).map(p -> {
            p.setName(updates.getName());
            p.setDescription(updates.getDescription());
            p.setSlug(updates.getSlug());
            if (updates.getCategory() != null) {
                p.setCategory(updates.getCategory());
            }
            if (updates.getBrand() != null) {
                p.setBrand(updates.getBrand());
            }
            return productRepository.save(p);
        }).orElseThrow(() -> new IllegalArgumentException("Product not found: " + id));
    }

    @Transactional
    public void deleteProduct(UUID id) {
        if (!productRepository.existsById(id)) {
            throw new IllegalArgumentException("Product not found: " + id);
        }
        productRepository.deleteById(id);
    }



    //
    //                          CATEGORY METHODS
    //

    public List<Category> listAllInCategory() {
        return categoryRepository.findAll();
    }

    public Optional<Category> getBySlug(String slug) {
        return categoryRepository.findBySlug(slug);
    }

    public Optional<Category> getByCategoryId(UUID id) {
        return categoryRepository.findById(id);
    }

    public Category create(Category category) {
        // compute fullPath: if parent exists, prepend parent's fullPath
        if (category.getParent() != null) {
            String parentPath = category.getParent().getFullPath();
            category.setFullPath(parentPath + "/" + category.getSlug());
        } else {
            category.setFullPath(category.getSlug());
        }
        return categoryRepository.save(category);
    }

    public Category update(UUID id, Category updates) {
        return categoryRepository.findById(id).map(cat -> {
            cat.setName(updates.getName());
            cat.setDescription(updates.getDescription());
            cat.setSlug(updates.getSlug());
            cat.setParent(updates.getParent());
            cat.setPosition(updates.getPosition());
            cat.setIsActive(updates.isIsActive());
            // recompute fullPath if slug or parent changed
            if (cat.getParent() != null) {
                cat.setFullPath(cat.getParent().getFullPath() + "/" + cat.getSlug());
            } else {
                cat.setFullPath(cat.getSlug());
            }
            return categoryRepository.save(cat);
        }).orElseThrow(() -> new IllegalArgumentException("Category not found: " + id));
    }

    @Transactional
    public void deleteCategory(UUID id) {
        if (!categoryRepository.existsById(id)) {
            throw new IllegalArgumentException("Category not found: " + id);
        }
        categoryRepository.deleteById(id);
    }

    //
    //                              BRAND METHODS
    //

    public List<Brand> listAllInBrand() {
        return brandRepository.findAll();
    }

    public Optional<Brand> getByBrandSlug(String slug) {
        return brandRepository.findBySlug(slug);
    }

    public Optional<Brand> getById(UUID id) {
        return brandRepository.findById(id);
    }

    public Brand create(Brand brand) {
        return brandRepository.save(brand);
    }

    public Brand update(UUID id, Brand updates) {
        return brandRepository.findById(id).map(b -> {
            b.setName(updates.getName());
            b.setDescription(updates.getDescription());
            b.setSlug(updates.getSlug());
            return brandRepository.save(b);
        }).orElseThrow(() -> new IllegalArgumentException("Brand not found: " + id));
    }

    @Transactional
    public void deleteBrand(UUID id) {
        if (!brandRepository.existsById(id)) {
            throw new IllegalArgumentException("Brand not found: " + id);
        }
        brandRepository.deleteById(id);
    }
}