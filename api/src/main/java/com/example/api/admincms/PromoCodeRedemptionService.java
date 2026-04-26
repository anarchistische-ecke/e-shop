package com.example.api.admincms;

import com.example.order.domain.Order;
import com.example.order.repository.OrderRepository;
import com.example.payment.service.PromoCodeRedemptionRecorder;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class PromoCodeRedemptionService implements PromoCodeRedemptionRecorder {

    private final PromoCodeRepository promoCodeRepository;
    private final OrderRepository orderRepository;

    public PromoCodeRedemptionService(PromoCodeRepository promoCodeRepository, OrderRepository orderRepository) {
        this.promoCodeRepository = promoCodeRepository;
        this.orderRepository = orderRepository;
    }

    @Override
    @Transactional
    public void recordPaidOrder(Order order) {
        if (order == null || order.isPromoCodeRedemptionRecorded() || !StringUtils.hasText(order.getPromoCode())) {
            return;
        }
        promoCodeRepository.findByCodeIgnoreCase(order.getPromoCode()).ifPresent(promoCode -> {
            promoCode.setRedemptionCount(promoCode.getRedemptionCount() + 1);
            promoCodeRepository.save(promoCode);
            order.setPromoCodeRedemptionRecorded(true);
            orderRepository.save(order);
        });
    }
}
