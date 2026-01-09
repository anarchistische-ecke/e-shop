package com.example.order.service;

import com.example.cart.domain.Cart;
import com.example.cart.domain.CartItem;
import com.example.cart.service.CartService;
import com.example.catalog.domain.ProductVariant;
import com.example.catalog.service.InventoryService;
import com.example.catalog.repository.ProductVariantRepository;
import com.example.common.domain.Money;
import com.example.order.domain.Order;
import com.example.order.domain.OrderItem;
import com.example.order.repository.OrderItemRepository;
import com.example.order.repository.OrderRepository;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class OrderService {
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final CartService cartService;
    private final InventoryService inventoryService;
    private final ProductVariantRepository variantRepository;

    @Autowired
    public OrderService(OrderRepository orderRepository,
                        OrderItemRepository orderItemRepository,
                        CartService cartService,
                        InventoryService inventoryService,
                        ProductVariantRepository variantRepository) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.cartService = cartService;
        this.inventoryService = inventoryService;
        this.variantRepository = variantRepository;
    }

    public Order createOrderFromCart(UUID cartId) {
        return createOrderFromCart(cartId, null, null);
    }

    public Order createOrderFromCart(UUID cartId, UUID customerIdOverride, String receiptEmail) {
        Cart cart = cartService.getCartById(cartId);
        long total = cart.getItems().stream().mapToLong(CartItem::getTotalAmount).sum();
        Money totalMoney = Money.of(total, "RUB");
        String baseKey = "order-cart-" + cartId;

        UUID customerId = customerIdOverride != null
                ? customerIdOverride
                : (cart.getCustomerId() != null ? cart.getCustomerId() : cartId);
        Order order = new Order(customerId, "PENDING", totalMoney);
        order.setReceiptEmail(receiptEmail);
        order.setPublicToken(generatePublicToken());

        for (CartItem ci : cart.getItems()) {
            String itemKey = baseKey + "-" + ci.getVariantId();
            inventoryService.adjustStock(ci.getVariantId(), -ci.getQuantity(), itemKey, "ORDER");
            OrderItem oi = new OrderItem(ci.getVariantId(), ci.getQuantity(), ci.getUnitPrice());
            ProductVariant variant = variantRepository.findWithProductById(ci.getVariantId())
                    .orElseThrow(() -> new IllegalArgumentException("Variant not found: " + ci.getVariantId()));
            oi.setVariantName(variant.getName());
            oi.setSku(variant.getSku());
            if (variant.getProduct() != null) {
                oi.setProductName(variant.getProduct().getName());
            }
            order.addItem(oi);
        }

        order = orderRepository.save(order);
        cartService.clearCart(cartId);
        return order;
    }

    public void updateOrderStatus(UUID orderId, String status) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found: " + orderId));
        order.setStatus(status);
        orderRepository.save(order);
    }

    public List<Order> getOrdersByCustomerId(UUID customerId) {
        return orderRepository.findByCustomerId(customerId);
    }

    public List<Order> getAllOrders() {
        return orderRepository.findAll();
    }

    public Order findById(UUID id) {
        return orderRepository.findWithItemsById(id)
                .orElseThrow(() -> new IllegalArgumentException("Order not found: " + id));
    }

    public Order findByPublicToken(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("Order token is required");
        }
        return orderRepository.findByPublicToken(token)
                .orElseThrow(() -> new IllegalArgumentException("Order not found"));
    }

    private String generatePublicToken() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
