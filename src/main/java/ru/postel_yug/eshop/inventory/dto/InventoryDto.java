package ru.postel_yug.eshop.inventory.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class InventoryDto {
    private UUID variantId;
    private Integer qty;
    private Integer reservations;
    private LocalDateTime updatedAt;
}
