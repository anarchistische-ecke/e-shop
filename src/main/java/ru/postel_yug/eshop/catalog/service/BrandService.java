package ru.postel_yug.eshop.catalog.service;

import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import ru.postel_yug.eshop.catalog.entity.Brand;
import ru.postel_yug.eshop.catalog.repository.BrandRepository;

import java.util.*;

@Service
public class BrandService {
    private final BrandRepository brandRepository;

    public BrandService(BrandRepository brandRepository) {
        this.brandRepository = brandRepository;
    }

    public List<Brand> getAllBrands() {
        return brandRepository.findAll();
    }

    public Brand getBrandBySlug(String slug) {
        return brandRepository.findBySlug(slug)
                .orElseThrow(() -> new EntityNotFoundException("Brand '" + slug + "' is not found"));
    }
}
