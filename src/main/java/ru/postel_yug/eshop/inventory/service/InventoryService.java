package ru.postel_yug.eshop.inventory.service;

import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.postel_yug.eshop.inventory.entity.Inventory;
import ru.postel_yug.eshop.inventory.repository.InventoryRepository;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class InventoryService {

    private final InventoryRepository inventoryRepository;

    public InventoryService (InventoryRepository inventoryRepository) {
        this.inventoryRepository = inventoryRepository;
    }

    public Inventory getInventory(UUID variantId) {
        return inventoryRepository.findById(variantId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Варинат " + variantId + " не найден в стоке"
                ));
    }

    @Transactional
    public Inventory updateStock(UUID variantId, int newQty) {
        Inventory inv = getInventory(variantId);
        if (newQty < inv.getReservations()) {
            throw new IllegalArgumentException("Нельзя установаить количества " + newQty +
                    ", так как уже " + inv.getReservations() + " зарезервировано");
        }
        inv.setQty(newQty);
        inv.setUpdatedAt(LocalDateTime.now());
        return inventoryRepository.save(inv);
    }

    public Inventory reserveStock(UUID variantId, int reserveQty) {
        Inventory inv = getInventory(variantId);
        int available = inv.getQty() - inv.getReservations();
        if (reserveQty > available) {
            throw new IllegalStateException("Недостаточно товара для резервирования. Запрошено: " +
                    reserveQty+ ", доступно: " + available);
        }
        inv.setReservations(inv.getReservations() + reserveQty);
        inv.setUpdatedAt(LocalDateTime.now());
        return inventoryRepository.save(inv);
    }
}
