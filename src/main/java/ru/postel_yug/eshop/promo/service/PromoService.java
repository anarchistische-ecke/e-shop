package ru.postel_yug.eshop.promo.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import ru.postel_yug.eshop.promo.entity.Promo;
import ru.postel_yug.eshop.promo.repository.PromoRepository;

import java.math.BigDecimal;
import java.time.LocalDate;

@Service
public class PromoService {
    @Autowired
    private PromoRepository promoRepo;

    public BigDecimal calculateDiscount(String code, BigDecimal orderAmount) {
        Promo promo = promoRepo.findByCode(code.toUpperCase())
                .orElseThrow(() -> new IllegalArgumentException("Invalid promo code"));
        // Проверка дат
        LocalDate today = LocalDate.now();
        if(promo.getValidFrom() != null && today.isBefore(promo.getValidFrom())) {
            throw new IllegalArgumentException("Promo code not active yet");
        }
        if(promo.getValidTo() != null && today.isAfter(promo.getValidTo())) {
            throw new IllegalArgumentException("Promo code expired");
        }
        // Проверка минимальной суммы
        if(((Promo) promo).getMinOrderAmount() != null && orderAmount.compareTo(promo.getMinOrderAmount()) < 0) {
            throw new IllegalArgumentException("Order amount too low for this promo");
        }
        // Проверка доступности использования
        if(promo.getUsageLimit() != null && promo.getUsedCount() >= promo.getUsageLimit()) {
            throw new IllegalArgumentException("Promo code usage limit reached");
        }
        // Вычисление скидки
        BigDecimal discount = BigDecimal.ZERO;
        if("FIXED".equals(promo.getDiscountType())) {
            discount = promo.getDiscountValue();
        } else if("PERCENT".equals(promo.getDiscountType())) {
            discount = orderAmount.multiply(promo.getDiscountValue().divide(new BigDecimal("100")));
        }
        // Ограничим скидку суммой заказа (не даем уйти в отрицательное)
        if(discount.compareTo(orderAmount) > 0) {
            discount = orderAmount;
        }
        return discount;
    }

    public void markPromoUsed(String code) {
        promoRepo.findByCode(code.toUpperCase()).ifPresent(promo -> {
            if(promo.getUsageLimit() != null) {
                promo.setUsedCount(promo.getUsedCount() + 1);
                promoRepo.save(promo);
            }
        });
    }
}

