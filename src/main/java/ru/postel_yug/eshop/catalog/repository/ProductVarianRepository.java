package ru.postel_yug.eshop.catalog.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.postel_yug.eshop.catalog.entity.ProductVariant;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductVarianRepository extends JpaRepository<ProductVariant, UUID> {
    Optional<ProductVariant> findBySlug(String slug);

    List<ProductVariant> findByProductId(UUID productId);
}
