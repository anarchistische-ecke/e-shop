package com.example.catalog.service;

import com.example.catalog.domain.ProductVariant;
import com.example.catalog.domain.StockAdjustment;
import com.example.catalog.repository.ProductVariantRepository;
import com.example.catalog.repository.StockAdjustmentRepository;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Optional;
import java.util.UUID;

@Service
@Transactional
public class InventoryService {

    private final ProductVariantRepository variantRepository;
    private final StockAdjustmentRepository adjustmentRepository;

    @Autowired
    public InventoryService(ProductVariantRepository variantRepository, StockAdjustmentRepository adjustmentRepository) {
        this.variantRepository = variantRepository;
        this.adjustmentRepository = adjustmentRepository;
    }

    public AdjustmentResult adjustStock(UUID variantId, int delta, String idempotencyKey, String reason) {
        if (!StringUtils.hasText(idempotencyKey)) {
            throw new IllegalArgumentException("Idempotency key is required for stock adjustment");
        }
        Optional<StockAdjustment> existing = adjustmentRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            if (!existing.get().getVariant().getId().equals(variantId)) {
                throw new IllegalStateException("Idempotency key уже использован для другого варианта.");
            }
            ProductVariant variant = existing.get().getVariant();
            return new AdjustmentResult(variant, false, existing.get());
        }

        ProductVariant variant = variantRepository.findById(variantId)
                .orElseThrow(() -> new IllegalArgumentException("Variant not found: " + variantId));

        int newStock = variant.getStockQuantity() + delta;
        if (newStock < 0) {
            throw new IllegalStateException("Недостаточно запаса. Текущее количество: " + variant.getStockQuantity());
        }
        variant.setStockQuantity(newStock);
        variantRepository.save(variant);

        StockAdjustment adjustment = new StockAdjustment(variant, delta, newStock, idempotencyKey, reason);
        adjustmentRepository.save(adjustment);

        return new AdjustmentResult(variant, true, adjustment);
    }

    public record AdjustmentResult(ProductVariant variant, boolean applied, StockAdjustment adjustment) {}
}
