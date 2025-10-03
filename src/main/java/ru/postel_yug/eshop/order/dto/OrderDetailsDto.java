package ru.postel_yug.eshop.order.dto;

import java.math.BigDecimal;
import java.util.List;

public class OrderDetailsDto extends OrderDto {
    private List<OrderItemDto> items;
    private BigDecimal shippingPrice;
    private BigDecimal totalDiscount;
    private String tracking;
    // ...
}
