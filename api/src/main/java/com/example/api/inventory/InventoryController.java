package com.example.api.inventory;

import com.example.catalog.service.InventoryService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/inventory")
public class InventoryController {

    private final InventoryService inventoryService;

    @Autowired
    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @PostMapping("/adjust")
    public ResponseEntity<StockAdjustmentResponse> adjustStock(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody StockAdjustmentRequest request) {
        if (!StringUtils.hasText(idempotencyKey)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
        var result = inventoryService.adjustStock(request.getVariantId(), request.getDelta(), idempotencyKey, request.getReason());
        StockAdjustmentResponse response = new StockAdjustmentResponse(
                request.getVariantId(),
                result.variant().getStockQuantity(),
                result.applied(),
                idempotencyKey
        );
        return result.applied()
                ? ResponseEntity.status(HttpStatus.CREATED).body(response)
                : ResponseEntity.ok(response);
    }

    public static class StockAdjustmentRequest {
        @NotNull
        private UUID variantId;
        @NotNull
        private Integer delta;
        private String reason;

        public UUID getVariantId() {
            return variantId;
        }

        public void setVariantId(UUID variantId) {
            this.variantId = variantId;
        }

        public Integer getDelta() {
            return delta;
        }

        public void setDelta(Integer delta) {
            this.delta = delta;
        }

        public String getReason() {
            return reason;
        }

        public void setReason(String reason) {
            this.reason = reason;
        }
    }

    public static class StockAdjustmentResponse {
        private UUID variantId;
        private int stock;
        private boolean applied;
        private String idempotencyKey;

        public StockAdjustmentResponse(UUID variantId, int stock, boolean applied, String idempotencyKey) {
            this.variantId = variantId;
            this.stock = stock;
            this.applied = applied;
            this.idempotencyKey = idempotencyKey;
        }

        public UUID getVariantId() {
            return variantId;
        }

        public void setVariantId(UUID variantId) {
            this.variantId = variantId;
        }

        public int getStock() {
            return stock;
        }

        public void setStock(int stock) {
            this.stock = stock;
        }

        public boolean isApplied() {
            return applied;
        }

        public void setApplied(boolean applied) {
            this.applied = applied;
        }

        public String getIdempotencyKey() {
            return idempotencyKey;
        }

        public void setIdempotencyKey(String idempotencyKey) {
            this.idempotencyKey = idempotencyKey;
        }
    }
}
