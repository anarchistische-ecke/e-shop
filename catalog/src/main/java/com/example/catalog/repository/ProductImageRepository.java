package com.example.catalog.repository;

import com.example.catalog.domain.ProductImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ProductImageRepository extends JpaRepository<ProductImage, UUID> {

    List<ProductImage> findByProduct_Id(UUID productId);

    List<ProductImage> findByProduct_IdOrderByPositionAscCreatedAtDesc(UUID productId);

}
