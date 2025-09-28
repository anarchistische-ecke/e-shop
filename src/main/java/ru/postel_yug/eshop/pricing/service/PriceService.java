package ru.postel_yug.eshop.pricing.service;

import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;
import ru.postel_yug.eshop.catalog.entity.ProductVariant;
import ru.postel_yug.eshop.catalog.repository.ProductVariantRepository;
import ru.postel_yug.eshop.pricing.dto.PriceDto;
import ru.postel_yug.eshop.pricing.entity.Price;
import ru.postel_yug.eshop.pricing.mapper.PriceMapper;
import ru.postel_yug.eshop.pricing.repository.PriceRepository;

import java.util.Optional;
import java.util.UUID;

@Service
public class PriceService {
    private final PriceRepository priceRepository;
    private final PriceMapper priceMapper;
    private final ProductVariantRepository productVariantRepository;

    public PriceService(PriceRepository priceRepository, PriceMapper priceMapper, ProductVariantRepository productVariantRepository) {
        this.priceRepository = priceRepository;
        this.priceMapper = priceMapper;
        this.productVariantRepository = productVariantRepository;
    }

   @Transactional
    public PriceDto getPriceByVariantId(UUID variantId) {
        Price price = priceRepository.findByVariant_Id(variantId).orElseThrow(() -> new RuntimeException("Цена на данный вариант не найдена " + variantId));
        return priceMapper.toDto(price);
    }

    @Transactional
    public PriceDto createPrice(PriceDto priceDto) {
        UUID variantId = priceDto.getVariantId();
        Optional<Price> existing = priceRepository.findByVariant_Id(variantId);
        if (existing.isPresent()) {
            throw new RuntimeException("Цена уже существует для варианта " + variantId);
        }
        ProductVariant variant = productVariantRepository.findById(variantId)
                .orElseThrow(() -> new RuntimeException("Variant not found: " + variantId));

        Price price = new Price();
        price.setAmount(priceDto.getAmount());
        price.setOldAmount(priceDto.getOldAmount());
        price.setCurrency(priceDto.getCurrency());
        price.setVatRate(priceDto.getVatRate());

        ProductVariant variantProxy = new ProductVariant();
        variantProxy.setId(variantId);
        price.setProductVariant(variantProxy);

        Price saved = priceRepository.save(price);

        return priceMapper.toDto(saved);
    }

    @Transactional
    public PriceDto updatePrice(UUID variantId, PriceDto priceDto) {
        Price price = priceRepository.findByVariant_Id(variantId)
                .orElseThrow(() -> new RuntimeException("Цена не найдена для этого варианта " + variantId));
        if (priceDto.getOldAmount() == null) {
            price.setOldAmount(price.getAmount());
        } else {
            price.setOldAmount(priceDto.getOldAmount());
        }

        price.setAmount(priceDto.getAmount());
        price.setCurrency(priceDto.getCurrency());
        price.setVatRate(priceDto.getVatRate());

        Price updated = priceRepository.save(price);
        return priceMapper.toDto(updated);
    }

    @Transactional
    public void deletePrice(UUID variantId) {
        Price price = priceRepository.findByVariant_Id(variantId)
                .orElseThrow(() -> new RuntimeException("Цена не найдена для этого варианта " + variantId));
                priceRepository.delete(price);
    }
}
