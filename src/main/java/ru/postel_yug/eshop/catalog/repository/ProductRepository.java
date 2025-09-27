package ru.postel_yug.eshop.catalog.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.postel_yug.eshop.catalog.entity.Product;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductRepository extends JpaRepository<Product, UUID> {
    Optional<Product> findBySlug(String slug);

    List<Product> findByCategoryId(UUID categoryId);

    List<Product> findByBrandSlug(String slug);

    List<Product> findByBrandId(UUID brandId);
}
