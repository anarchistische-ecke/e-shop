package com.example.catalog.repository;

import com.example.catalog.domain.ProductVariant;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProductVariantRepository extends JpaRepository<ProductVariant, UUID> {
    @EntityGraph(attributePaths = "product")
    Optional<ProductVariant> findWithProductById(UUID id);

    @EntityGraph(attributePaths = "product")
    Optional<ProductVariant> findBySku(String sku);

    @EntityGraph(attributePaths = "product")
    List<ProductVariant> findByIdIn(Collection<UUID> ids);
}
