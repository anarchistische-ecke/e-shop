package ru.postel_yug.eshop.catalog.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.postel_yug.eshop.catalog.dto.BrandDto;
import ru.postel_yug.eshop.catalog.mapper.BrandMapper;
import ru.postel_yug.eshop.catalog.service.BrandService;

import java.util.List;

@RestController
@RequestMapping("/api/v1/brands")
public class BrandController {
    private final BrandService brandService;
    private final BrandMapper brandMapper;

    public BrandController (BrandService brandService, BrandMapper brandMapper) {
        this.brandService = brandService;
        this.brandMapper = brandMapper;
    }

    @GetMapping
    public List<BrandDto> getAllBrands() {
        return brandMapper.toDtoList(brandService.getAllBrands());
    }

    @GetMapping("/{slug}")
    public BrandDto getBrandBySlug(@PathVariable String slug) {
        return brandMapper.toDto(brandService.getBrandBySlug(slug));
    }

}
