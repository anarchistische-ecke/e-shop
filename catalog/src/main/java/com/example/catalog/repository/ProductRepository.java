package com.example.catalog.repository;

import com.example.catalog.domain.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProductRepository extends JpaRepository<Product, UUID> {
    Optional<Product> findBySlug(String slug);
    List<Product> findByBrand_Slug(String brandSlug);
    List<Product> findDistinctByCategories_FullPathStartingWith(String fullPathPrefix);
    List<Product> findDistinctByCategories_FullPathStartingWithAndBrand_Slug(String fullPathPrefix, String brandSlug);
}
