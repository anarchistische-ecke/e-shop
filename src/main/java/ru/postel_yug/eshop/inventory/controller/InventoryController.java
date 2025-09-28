package ru.postel_yug.eshop.inventory.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import ru.postel_yug.eshop.inventory.dto.InventoryDto;
import ru.postel_yug.eshop.inventory.entity.Inventory;
import ru.postel_yug.eshop.inventory.mapper.InventoryMapper;
import ru.postel_yug.eshop.inventory.service.InventoryService;

import java.util.UUID;

@RestController
public class InventoryController {
    private final InventoryService inventoryService;
    private final InventoryMapper inventoryMapper;

    public InventoryController(InventoryService inventoryService, InventoryMapper inventoryMapper) {
        this.inventoryService = inventoryService;
        this.inventoryMapper = inventoryMapper;
    }

    @GetMapping("/{variantId}")
    public InventoryDto getInventory(@PathVariable UUID variantId) {
        Inventory inventory = inventoryService.getInventory(variantId);
        return inventoryMapper.toDto(inventory);
    }

    @PutMapping("/{variantId}")
    public InventoryDto updateInventory(
            @PathVariable UUID variantId,
            @RequestBody InventoryDto inventoryRequest
    ) {
        int newQty = inventoryRequest.getQty();
        Inventory updated = inventoryService.updateStock(variantId, newQty);
        return inventoryMapper.toDto(updated);
    }

    @PostMapping("/{variantId}/reserve")
    @ResponseStatus(HttpStatus.OK)
    public InventoryDto reserveInventory(
            @PathVariable UUID variantId,
            @RequestParam("qty") int reserveQty
    ) {
        Inventory updated = inventoryService.reserveStock(variantId, reserveQty);
        return inventoryMapper.toDto(updated);
    }
}
