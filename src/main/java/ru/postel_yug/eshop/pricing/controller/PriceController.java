package ru.postel_yug.eshop.pricing.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import ru.postel_yug.eshop.pricing.dto.PriceDto;
import ru.postel_yug.eshop.pricing.service.PriceService;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/prices")
public class PriceController {

    private final PriceService priceService;

    public PriceController(PriceService priceService) {
        this.priceService = priceService;
    }

    @GetMapping("{/variantId}")
    public PriceDto getPrice(@PathVariable UUID variantId) {
        return priceService.getPriceByVariantId(variantId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PriceDto createPrice(@Valid @RequestBody PriceDto priceDto) {
        return priceService.createPrice(priceDto);
    }

    @PutMapping("/{variantId}")
    public PriceDto updatePrice(@PathVariable UUID variantId,
                                @Valid @RequestBody PriceDto priceDto) {
        priceDto.setVariantId(variantId);
        return priceService.updatePrice(variantId, priceDto);
    }

    @DeleteMapping("/{variantId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletePrice(@PathVariable UUID variantId) {
        priceService.deletePrice(variantId);
    }
}
