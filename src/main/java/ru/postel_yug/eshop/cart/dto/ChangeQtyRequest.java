package ru.postel_yug.eshop.cart.dto;

import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public class ChangeQtyRequest {
    @Min(1)
    private int quantity;
}
