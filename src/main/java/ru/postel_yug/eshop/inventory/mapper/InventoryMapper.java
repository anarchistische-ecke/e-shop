package ru.postel_yug.eshop.inventory.mapper;

import org.mapstruct.Mapper;
import ru.postel_yug.eshop.inventory.dto.InventoryDto;
import ru.postel_yug.eshop.inventory.entity.Inventory;

@Mapper(componentModel = "spring")
public interface InventoryMapper {
    InventoryDto toDto(Inventory inventory);

    Inventory toEntity(InventoryDto dto);
}
