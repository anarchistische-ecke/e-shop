package ru.postel_yug.eshop.cart.mapper;

import ru.postel_yug.eshop.cart.dto.CartDto;
import ru.postel_yug.eshop.cart.dto.CartItemDto;
import ru.postel_yug.eshop.cart.entity.Cart;
import ru.postel_yug.eshop.cart.entity.CartItem;
import ru.postel_yug.eshop.catalog.dto.ProductInfo;
import ru.postel_yug.eshop.catalog.service.ProductService;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class CartMapper {

    private final ProductService productService;

    public CartMapper(ProductService productService) {
        this.productService = productService;
    }

    public CartDto toDto(Cart cart) {
        List<CartItemDto> itemsDto = cart.getItems().stream()
                .map(this::mapItem)
                .collect(Collectors.toList());

        double total = itemsDto.stream()
                .mapToDouble(item -> item.getProduct().getPrice() * item.getQuantity())
                .sum();

        CartDto dto = new CartDto();
        dto.setId(cart.getId());
        dto.setUserId(cart.getUserId());
        dto.setItems(itemsDto);
        dto.setTotalPrice(total);
        return dto;
    }

    private CartItemDto mapItem(CartItem item) {
        ProductInfo product = productService.getProductById(item.getProductId());
        return new CartItemDto(product, item.getQuantity());
    }
}
