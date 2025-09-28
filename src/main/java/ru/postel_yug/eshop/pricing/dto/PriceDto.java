package ru.postel_yug.eshop.pricing.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.util.UUID;

public class PriceDto {
    @NotNull(message = "variantId обязателен")
    private UUID variantId;

    @NotNull(message = "Текущая цена обязательна")
    @DecimalMin(value = "0.0", inclusive = false, message = "Цена должна быть больше 0")
    private BigDecimal amount;

    private BigDecimal oldAmount;

    @NotBlank(message = "Код валюты обязателен")
    @Size(min = 3, max = 3, message = "Код валюты должен состоять из 3 символов")
    private String currency;

    @NotNull(message = "Ставка НДС обязательна")
    @DecimalMin(value = "0.0", inclusive = true, message = "Ставка НДС не может быть отрицательной")
    @DecimalMax(value = "1.0", inclusive = true, message = "Ставка НДС не может превышать 100%")
    private BigDecimal vatRate;

    public PriceDto(UUID variantId, BigDecimal amount, BigDecimal oldAmount, String currency, BigDecimal vatRate) {
        this.variantId = variantId;
        this.amount = amount;
        this.oldAmount = oldAmount;
        this.currency = currency;
        this.vatRate = vatRate;
    }

    public @NotNull(message = "variantId обязателен") UUID getVariantId() {
        return variantId;
    }

    public void setVariantId(@NotNull(message = "variantId обязателен") UUID variantId) {
        this.variantId = variantId;
    }

    public @NotNull(message = "Текущая цена обязательна") @DecimalMin(value = "0.0", inclusive = false, message = "Цена должна быть больше 0") BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(@NotNull(message = "Текущая цена обязательна") @DecimalMin(value = "0.0", inclusive = false, message = "Цена должна быть больше 0") BigDecimal amount) {
        this.amount = amount;
    }

    public BigDecimal getOldAmount() {
        return oldAmount;
    }

    public void setOldAmount(BigDecimal oldAmount) {
        this.oldAmount = oldAmount;
    }

    public @NotBlank(message = "Код валюты обязателен") @Size(min = 3, max = 3, message = "Код валюты должен состоять из 3 символов") String getCurrency() {
        return currency;
    }

    public void setCurrency(@NotBlank(message = "Код валюты обязателен") @Size(min = 3, max = 3, message = "Код валюты должен состоять из 3 символов") String currency) {
        this.currency = currency;
    }

    public @NotNull(message = "Ставка НДС обязательна") @DecimalMin(value = "0.0", inclusive = true, message = "Ставка НДС не может быть отрицательной") @DecimalMax(value = "1.0", inclusive = true, message = "Ставка НДС не может превышать 100%") BigDecimal getVatRate() {
        return vatRate;
    }

    public void setVatRate(@NotNull(message = "Ставка НДС обязательна") @DecimalMin(value = "0.0", inclusive = true, message = "Ставка НДС не может быть отрицательной") @DecimalMax(value = "1.0", inclusive = true, message = "Ставка НДС не может превышать 100%") BigDecimal vatRate) {
        this.vatRate = vatRate;
    }
}
