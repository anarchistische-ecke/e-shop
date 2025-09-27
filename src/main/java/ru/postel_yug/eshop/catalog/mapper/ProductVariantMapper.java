package ru.postel_yug.eshop.catalog.mapper;

import org.mapstruct.Mapper;
import ru.postel_yug.eshop.catalog.dto.ProductVariantDto;
import ru.postel_yug.eshop.catalog.entity.ProductVariant;

import java.util.List;

@Mapper(componentModel = "spring")
public interface ProductVariantMapper {
    ProductVariantDto toDto(ProductVariant productVariant);
    List<ProductVariantDto> toDtoList(List<ProductVariant> variants);
}
