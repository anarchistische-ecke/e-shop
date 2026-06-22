package com.example.catalog.repository;

import com.example.catalog.domain.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CategoryRepository extends JpaRepository<Category, UUID> {
    Optional<Category> findBySlug(String slug);
    @Query("select c from Category c where lower(trim(c.slug)) = lower(trim(:slug))")
    List<Category> findByNormalizedSlug(@Param("slug") String slug);
    List<Category> findBySlugIn(Collection<String> slugs);
    List<Category> findByParent_Id(UUID parentId);
}
