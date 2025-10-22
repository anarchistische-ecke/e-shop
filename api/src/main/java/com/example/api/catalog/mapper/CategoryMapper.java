package com.example.api.catalog.mapper;

import com.example.api.catalog.dto.CategoryDto;
import com.example.catalog.domain.Category;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface CategoryMapper {
    @Mapping(target = "parentId", source = "parent.id")
    @Mapping(target = "childrenCount", expression = "java(category.getChildren().size())")
    CategoryDto toDto(Category category);
}
