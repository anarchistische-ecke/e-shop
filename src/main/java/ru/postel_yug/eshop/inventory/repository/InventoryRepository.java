package ru.postel_yug.eshop.inventory.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.postel_yug.eshop.inventory.entity.Inventory;

import java.util.UUID;

public interface InventoryRepository extends JpaRepository<Inventory, UUID> {

}
