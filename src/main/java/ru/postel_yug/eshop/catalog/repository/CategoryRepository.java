package ru.postel_yug.eshop.catalog.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.postel_yug.eshop.catalog.entity.Category;

import java.util.Optional;
import java.util.UUID;

public interface CategoryRepository extends JpaRepository <Category, UUID> {
    Optional<Category> findBySlug(String slug);
}
