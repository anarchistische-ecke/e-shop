package ru.postel_yug.eshop.catalog.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.postel_yug.eshop.catalog.dto.ProductDto;
import ru.postel_yug.eshop.catalog.entity.Product;
import ru.postel_yug.eshop.catalog.mapper.ProductMapper;
import ru.postel_yug.eshop.catalog.service.ProductService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/products")
public class ProductController {
    private final ProductService productService;
    private final ProductMapper productMapper;

    public ProductController(ProductService productService, ProductMapper productMapper) {
        this.productService = productService;
        this.productMapper = productMapper;
    }

    @GetMapping
    public List<ProductDto> getAllProducts(
            @RequestParam(name = "category", required = false) UUID categoryId,
            @RequestParam(name = "brand", required = false) UUID brandId
    ) {
        List<Product> products;
        if (categoryId != null) {
            products = productService.getProductsByCategory(categoryId);
        } else if (brandId != null) {
            products = productService.getProductsByBrand(brandId);
        } else {
            products = productService.getAllProducts();
        }
        return productMapper.toDtoList(products);
    }

    @GetMapping("/{slug}")
    public ProductDto getProductBySlug(@RequestParam String slug) {
        return productMapper.toDto(productService.findBySlug(slug));
    }
}
