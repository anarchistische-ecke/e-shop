package com.example.api.catalog.mapper;

import com.example.api.catalog.dto.BrandDto;
import com.example.catalog.domain.Brand;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface BrandMapper {
    BrandDto toDto(Brand brand);
}
