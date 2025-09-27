package ru.postel_yug.eshop.catalog.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import ru.postel_yug.eshop.catalog.dto.ProductDto;
import ru.postel_yug.eshop.catalog.entity.Product;

import java.util.List;

@Mapper(componentModel = "spring", uses = {CategoryMapper.class, BrandMapper.class, ProductVariantMapper.class})
public interface ProductMapper {
    @Mapping(source = "category.name", target = "categoryName")
    @Mapping(source = "brand.name", target = "brandName")
    ProductDto toDto(Product product);

    List<ProductDto> toDtoList(List<Product> products);
}
