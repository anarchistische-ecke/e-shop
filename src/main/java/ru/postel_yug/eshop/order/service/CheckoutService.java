package ru.postel_yug.eshop.order.service;

import ru.postel_yug.eshop.cart.dto.CartDto;
import ru.postel_yug.eshop.cart.dto.CartItemDto;
import ru.postel_yug.eshop.cart.service.CartService;
import ru.postel_yug.eshop.catalog.dto.ProductInfo;
import ru.postel_yug.eshop.catalog.service.ProductService;
import ru.postel_yug.eshop.order.dto.OrderDto;
import ru.postel_yug.eshop.payment.exception.PaymentFailedException;
import ru.postel_yug.eshop.payment.service.PaymentService;
import ru.postel_yug.eshop.shipping.service.ShippingService;

public class CheckoutService {

    private final CartService cartService;
    private final ProductService productService;
    private final PaymentService paymentService;
    private final ShippingService shippingService;

    public CheckoutService(CartService cartService,
                           ProductService productService,
                           PaymentService paymentService,
                           ShippingService shippingService) {
        this.cartService = cartService;
        this.productService = productService;
        this.paymentService = paymentService;
        this.shippingService = shippingService;
    }


    public OrderDto checkout(Long userId, String paymentMethod, String deliveryType) {
        CartDto cartDto = cartService.getCartByUserId(userId);

        if (cartDto.getItems() == null || cartDto.getItems().isEmpty()) {
            throw new IllegalStateException("Cart is empty.");
        }

        // validate each product exists and has enough stock
        for (CartItemDto item : cartDto.getItems()) {
            ProductInfo product = productService.getProductById(item.getProduct().getId());
            if (product == null) {
                throw new IllegalStateException("Product not found: " + item.getProduct().getId());
            }
            if (item.getQuantity() > product.getAvailableQuantity()) {
                throw new IllegalStateException("Insufficient stock for " + product.getName());
            }
        }

        boolean paymentOk = paymentService.processPayment(userId, cartDto.getTotalPrice(), paymentMethod);
        if (!paymentOk) {
            throw new PaymentFailedException("Payment processing failed");
        }

        double shippingCost = shippingService.calculateShippingCost(deliveryType, cartDto.getTotalPrice());

        OrderDto orderDto = new OrderDto();
        orderDto.setUserId(userId);
        orderDto.setItems(cartDto.getItems());
        orderDto.setSubtotal(cartDto.getTotalPrice());
        orderDto.setShippingCost(shippingCost);
        orderDto.setTotal(cartDto.getTotalPrice() + shippingCost);

        // empty the cart
        cartService.clearCart(userId);
        return orderDto;
    }
}
