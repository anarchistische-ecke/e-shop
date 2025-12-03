package com.example.order.service;

import com.example.cart.domain.Cart;
import com.example.cart.domain.CartItem;
import com.example.cart.repository.CartRepository;
import com.example.catalog.service.InventoryService;
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
    private final CartRepository cartRepository;
    private final InventoryService inventoryService;

    @Autowired
    public OrderService(OrderRepository orderRepository,
                        OrderItemRepository orderItemRepository,
                        CartRepository cartRepository,
                        InventoryService inventoryService) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.cartRepository = cartRepository;
        this.inventoryService = inventoryService;
    }

    public Order createOrderFromCart(UUID cartId) {
        Cart cart = cartRepository.findById(cartId)
                .orElseThrow(() -> new IllegalArgumentException("Cart not found: " + cartId));
        // calculate total amount in smallest units
        long total = cart.getItems().stream().mapToLong(CartItem::getTotalAmount).sum();
        Money totalMoney = Money.of(total, "RUB");
        // idempotency key per cart+variant so repeated requests won't double-decrement stock
        String baseKey = "order-cart-" + cartId;

        Order order = new Order(cart.getCustomerId(), "PENDING", totalMoney);
        for (CartItem ci : cart.getItems()) {
            String itemKey = baseKey + "-" + ci.getVariantId();
            inventoryService.adjustStock(ci.getVariantId(), -ci.getQuantity(), itemKey, "ORDER");
            OrderItem oi = new OrderItem(ci.getVariantId(), ci.getQuantity(), ci.getUnitPrice());
            order.addItem(oi);
        }

        order = orderRepository.save(order);
        cart.getItems().clear();
        cartRepository.save(cart);
        return order;
    }

    public void updateOrderStatus(UUID orderId, String status) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found: " + orderId));
        order.setStatus(status);
        orderRepository.save(order);
    }

    public List<Order> getAllOrders() {
        return orderRepository.findAll();
    }

    public Order findById(UUID id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Order not found: " + id));
    }
}
