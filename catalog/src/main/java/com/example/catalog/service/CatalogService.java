package com.example.catalog.service;

import com.example.catalog.domain.Brand;
import com.example.catalog.domain.Category;
import com.example.catalog.domain.Product;
import com.example.catalog.domain.ProductImage;
import com.example.catalog.domain.ProductVariant;
import com.example.catalog.repository.BrandRepository;
import com.example.catalog.repository.CategoryRepository;
import com.example.catalog.repository.ProductRepository;
import com.example.catalog.repository.ProductImageRepository;
import com.example.catalog.repository.ProductVariantRepository;
import com.example.common.domain.Money;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@Transactional
public class CatalogService {
    private final ProductRepository productRepository;
    private final ProductVariantRepository variantRepository;
    private final BrandRepository brandRepository;
    private final CategoryRepository categoryRepository;
    private final ProductImageRepository imageRepository;

    @Autowired
    public CatalogService(ProductRepository productRepository, ProductVariantRepository variantRepository, BrandRepository brandRepository, CategoryRepository categoryRepository, ProductImageRepository imageRepository) {
        this.productRepository = productRepository;
        this.variantRepository = variantRepository;
        this.brandRepository = brandRepository;
        this.categoryRepository = categoryRepository;
        this.imageRepository = imageRepository;
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

    public ProductVariant updateVariant(UUID productId, UUID variantId, String name, Money price, int stock) {
        ProductVariant variant = variantRepository.findById(variantId)
                .orElseThrow(() -> new IllegalArgumentException("Variant not found: " + variantId));
        if (variant.getProduct() == null || !variant.getProduct().getId().equals(productId)) {
            throw new IllegalArgumentException("Variant does not belong to product: " + productId);
        }
        if (name != null && !name.isBlank()) {
            variant.setName(name);
        }
        if (price != null) {
            variant.setPrice(price);
        }
        variant.setStockQuantity(stock);
        return variantRepository.save(variant);
    }

    public ProductImage addProductImage(UUID productId, String url, String objectKey, int position, UUID variantId) {
        Product product = productRepository.findById(productId).orElseThrow(() -> new IllegalArgumentException("Product not found: " + productId));
        ProductVariant variant = null;
        if (variantId != null) {
            variant = variantRepository.findById(variantId)
                    .orElseThrow(() -> new IllegalArgumentException("Variant not found: " + variantId));
            if (variant.getProduct() == null || !variant.getProduct().getId().equals(productId)) {
                throw new IllegalArgumentException("Variant does not belong to product: " + productId);
            }
        }
        int safePosition = Math.max(0, position);
        ProductImage image = new ProductImage(product, url, objectKey, safePosition);
        image.setVariant(variant);
        product.addImage(image);
        return imageRepository.save(image);
    }

    public List<ProductImage> getProductImages(UUID productId) {
        List<ProductImage> images = imageRepository.findByProduct_IdOrderByPositionAscCreatedAtDesc(productId);
        images.forEach(img -> {
            if (img.getVariant() != null) {
                img.getVariant().getId(); // initialize lazy relation for mapping
            }
        });
        return images;
    }

    public String removeProductImage(UUID productId, UUID imageId) {
        ProductImage image = imageRepository.findById(imageId)
                .orElseThrow(() -> new IllegalArgumentException("Image not found: " + imageId));
        if (image.getProduct() == null || !image.getProduct().getId().equals(productId)) {
            throw new IllegalArgumentException("Image does not belong to product: " + productId);
        }
        String objectKey = image.getObjectKey();
        image.getProduct().removeImage(image);
        imageRepository.delete(image);
        return objectKey;
    }

    public ProductImage updateProductImage(UUID productId, UUID imageId, UUID variantId, Integer position) {
        ProductImage image = imageRepository.findById(imageId)
                .orElseThrow(() -> new IllegalArgumentException("Image not found: " + imageId));
        if (image.getProduct() == null || !image.getProduct().getId().equals(productId)) {
            throw new IllegalArgumentException("Image does not belong to product: " + productId);
        }
        ProductVariant variant = null;
        if (variantId != null) {
            variant = variantRepository.findById(variantId)
                    .orElseThrow(() -> new IllegalArgumentException("Variant not found: " + variantId));
            if (variant.getProduct() == null || !variant.getProduct().getId().equals(productId)) {
                throw new IllegalArgumentException("Variant does not belong to product: " + productId);
            }
        }
        image.setVariant(variant);
        if (position != null) {
            image.setPosition(Math.max(0, position));
        }
        return imageRepository.save(image);
    }

    public List<Product> getProducts(String categorySlug, String brandSlug) {
        boolean hasCategory = categorySlug != null && !categorySlug.isBlank();
        boolean hasBrand = brandSlug != null && !brandSlug.isBlank();
        String categoryPath = null;
        if (hasCategory) {
            categoryPath = resolveCategory(categorySlug).map(Category::getFullPath).orElse(null);
            if (categoryPath == null) {
                return List.of();
            }
        }
        List<Product> products;
        if (hasCategory && hasBrand) {
            products = productRepository.findDistinctByCategories_FullPathStartingWithAndBrand_Slug(categoryPath, brandSlug);
        } else if (hasCategory) {
            products = productRepository.findDistinctByCategories_FullPathStartingWith(categoryPath);
        } else if (hasBrand) {
            products = productRepository.findByBrand_Slug(brandSlug);
        } else {
            products = productRepository.findAll();
        }
        products.forEach(this::hydrateProduct);
        return products;
    }

    @Transactional
    public Optional<Product> getProduct(UUID id) {
        Optional<Product> product = productRepository.findById(id);
        product.ifPresent(this::hydrateProduct);
        return product;
    }

    public List<Product> getAllProducts() {
        List<Product> products = productRepository.findAll();
        products.forEach(this::hydrateProduct);
        return products;
    }

    @Transactional
    public Product updateProduct(UUID id, Product updates, boolean categoriesProvided) {
        return productRepository.findById(id).map(p -> {
            p.setName(updates.getName());
            p.setDescription(updates.getDescription());
            p.setSlug(updates.getSlug());
            if (categoriesProvided) {
                Set<Category> nextCategories = updates.getCategories() != null
                        ? new HashSet<>(updates.getCategories())
                        : new HashSet<>();
                p.setCategories(nextCategories);
            }
            if (updates.getBrand() != null) {
                p.setBrand(updates.getBrand());
            }
            Product saved = productRepository.save(p);
            hydrateProduct(saved);
            return saved;
        }).orElseThrow(() -> new IllegalArgumentException("Product not found: " + id));
    }

    @Transactional
    public void deleteProduct(UUID id) {
        if (!productRepository.existsById(id)) {
            throw new IllegalArgumentException("Product not found: " + id);
        }
        productRepository.deleteById(id);
    }

    private void hydrateProduct(Product product) {
        if (product == null) {
            return;
        }
        if (product.getCategories() != null) {
            product.getCategories().forEach(cat -> {
                if (cat != null) {
                    cat.getSlug();
                }
            });
        }
        if (product.getBrand() != null) {
            product.getBrand().getSlug();
        }
        if (product.getVariants() != null) {
            product.getVariants().size();
        }
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
            Category saved = categoryRepository.save(cat);
            updateChildrenFullPaths(saved);
            return saved;
        }).orElseThrow(() -> new IllegalArgumentException("Category not found: " + id));
    }

    @Transactional
    public void deleteCategory(UUID id) {
        if (!categoryRepository.existsById(id)) {
            throw new IllegalArgumentException("Category not found: " + id);
        }
        categoryRepository.deleteById(id);
    }

    private void updateChildrenFullPaths(Category parent) {
        if (parent == null) {
            return;
        }
        List<Category> children = categoryRepository.findByParent_Id(parent.getId());
        for (Category child : children) {
            if (child == null) {
                continue;
            }
            child.setFullPath(parent.getFullPath() + "/" + child.getSlug());
            Category saved = categoryRepository.save(child);
            updateChildrenFullPaths(saved);
        }
    }

    private Optional<Category> resolveCategory(String reference) {
        if (reference == null || reference.isBlank()) {
            return Optional.empty();
        }
        Optional<Category> bySlug = categoryRepository.findBySlug(reference);
        if (bySlug.isPresent()) {
            return bySlug;
        }
        try {
            return categoryRepository.findById(UUID.fromString(reference));
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
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
