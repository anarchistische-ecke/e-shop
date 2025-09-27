package ru.postel_yug.eshop.catalog.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.postel_yug.eshop.catalog.entity.Brand;

import java.util.Optional;
import java.util.UUID;

public interface BrandRepository extends JpaRepository<Brand, UUID> {
    Optional<Brand> findBySlug(String slug);
}
