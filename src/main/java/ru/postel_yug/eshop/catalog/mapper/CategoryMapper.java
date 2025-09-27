package ru.postel_yug.eshop.catalog.mapper;

import org.mapstruct.Mapper;
import ru.postel_yug.eshop.catalog.dto.CategoryDto;
import ru.postel_yug.eshop.catalog.entity.Category;

import java.util.List;

@Mapper(componentModel = "spring")
public interface CategoryMapper {
    CategoryDto toDto(Category category);
    List<CategoryDto> toDtoList(List<Category> categories);
}
