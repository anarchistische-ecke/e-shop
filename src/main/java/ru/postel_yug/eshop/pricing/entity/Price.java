package ru.postel_yug.eshop.pricing.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import ru.postel_yug.eshop.catalog.entity.ProductVariant;

import java.math.BigDecimal;
import java.util.*;

@Entity
@Table(name = "prices")
public class Price {
    @Id
    @GeneratedValue(generator = "UUID")
    @org.hibernate.annotations.GenericGenerator(
            name = "UUID", strategy = "org.hibernate.id.UUIDGenerator"
    )
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "variant_id", nullable = false)
    private ProductVariant productVariant;

    @NotNull(message = "Цена должна быть указана")
    @DecimalMin(value = "0.0", inclusive = false, message = "Цена должна быть больше 0")
    private BigDecimal amount;

    private BigDecimal oldAmount;

    @NotBlank(message = "Валюта должна быть указана")
    @Size(min = 3, max = 3, message = "Код валюты должен состоять из трех букв")
    private String currency;

    @NotNull(message = "Ставка НДС должна быть указана")
    @DecimalMin(value = "0.0", inclusive = true, message = "Ставка НДС не может быть отрицательной")
    @DecimalMax(value = "1.0", inclusive = true, message = "Ставка НДС не может быть больше 100%")
    private BigDecimal vatRate;

    public Price() {
    }

    public Price(UUID id, ProductVariant productVariant, BigDecimal amount, BigDecimal oldAmount, String currency, BigDecimal varRate) {
        this.id = id;
        this.productVariant = productVariant;
        this.amount = amount;
        this.oldAmount = oldAmount;
        this.currency = currency;
        this.vatRate = varRate;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public ProductVariant getProductVariant() {
        return productVariant;
    }

    public void setProductVariant(ProductVariant productVariant) {
        this.productVariant = productVariant;
    }

    public @NotNull(message = "Цена должна быть указана") @DecimalMin(value = "0.0", inclusive = false, message = "Цена должна быть больше 0") BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(@NotNull(message = "Цена должна быть указана") @DecimalMin(value = "0.0", inclusive = false, message = "Цена должна быть больше 0") BigDecimal amount) {
        this.amount = amount;
    }

    public BigDecimal getOldAmount() {
        return oldAmount;
    }

    public void setOldAmount(BigDecimal oldAmount) {
        this.oldAmount = oldAmount;
    }

    public @NotBlank(message = "Валюта должна быть указана") @Size(min = 3, max = 3, message = "Код валюты должен состоять из трех букв") String getCurrency() {
        return currency;
    }

    public void setCurrency(@NotBlank(message = "Валюта должна быть указана") @Size(min = 3, max = 3, message = "Код валюты должен состоять из трех букв") String currency) {
        this.currency = currency;
    }

    public @NotNull(message = "Ставка НДС должна быть указана") @DecimalMin(value = "0.0", inclusive = true, message = "Ставка НДС не может быть отрицательной") @DecimalMax(value = "1.0", inclusive = true, message = "Ставка НДС не может быть больше 100%") BigDecimal getVarRate() {
        return vatRate;
    }

    public void setVatRate(@NotNull(message = "Ставка НДС должна быть указана") @DecimalMin(value = "0.0", inclusive = true, message = "Ставка НДС не может быть отрицательной") @DecimalMax(value = "1.0", inclusive = true, message = "Ставка НДС не может быть больше 100%") BigDecimal varRate) {
        this.vatRate = varRate;
    }
}
