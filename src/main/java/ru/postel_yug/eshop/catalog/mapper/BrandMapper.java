package ru.postel_yug.eshop.catalog.mapper;

import org.mapstruct.Mapper;
import ru.postel_yug.eshop.catalog.dto.BrandDto;
import ru.postel_yug.eshop.catalog.entity.Brand;

import java.util.List;

@Mapper(componentModel = "spring")
public interface BrandMapper {
    BrandDto toDto(Brand brand);
    List<BrandDto> toDtoList(List<Brand> brands);
}
