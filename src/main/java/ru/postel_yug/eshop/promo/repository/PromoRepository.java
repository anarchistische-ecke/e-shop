package ru.postel_yug.eshop.promo.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.postel_yug.eshop.promo.entity.Promo;

import java.util.Optional;

public interface PromoRepository extends JpaRepository<Promo, Integer> {
    Optional<Promo> findByCode(String code);
}
