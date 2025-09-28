package ru.postel_yug.eshop.pricing.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.postel_yug.eshop.pricing.entity.Price;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PriceRepository extends JpaRepository<Price, UUID> {
    Optional<Price> findByVariant_Id(UUID variantId);

    void deleteByVariant_Id(UUID variantId);
}
