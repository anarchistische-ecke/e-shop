package ru.postel_yug.eshop.pricing.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import ru.postel_yug.eshop.pricing.dto.PriceDto;
import ru.postel_yug.eshop.pricing.entity.Price;

@Mapper(componentModel = "spring")
public interface PriceMapper {
    @Mapping(source = "variant.id", target = "variantId")
    PriceDto toDto(Price price);
}
