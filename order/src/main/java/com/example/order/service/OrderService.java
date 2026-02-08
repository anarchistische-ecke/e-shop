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

import java.time.OffsetDateTime;
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
        return createOrderFromCart(cartId, null, null, null, null);
    }

    public Order createOrderFromCart(UUID cartId, UUID customerIdOverride, String receiptEmail) {
        return createOrderFromCart(cartId, customerIdOverride, receiptEmail, null, null);
    }

    public Order createOrderFromCart(UUID cartId,
                                     UUID customerIdOverride,
                                     String receiptEmail,
                                     String managerSubject) {
        return createOrderFromCart(cartId, customerIdOverride, receiptEmail, managerSubject, null);
    }

    public Order createOrderFromCart(UUID cartId,
                                     UUID customerIdOverride,
                                     String receiptEmail,
                                     String managerSubject,
                                     DeliverySpec deliverySpec) {
        Cart cart = cartService.getCartById(cartId);
        long itemsTotal = cart.getItems().stream().mapToLong(CartItem::getTotalAmount).sum();
        String currency = cart.getItems().stream()
                .map(CartItem::getUnitPrice)
                .filter(money -> money != null && money.getCurrency() != null && !money.getCurrency().isBlank())
                .map(Money::getCurrency)
                .findFirst()
                .orElse("RUB");
        long deliveryAmount = deliverySpec != null && deliverySpec.amount() != null
                ? deliverySpec.amount().getAmount()
                : 0L;
        if (deliverySpec != null && deliverySpec.amount() != null) {
            String deliveryCurrency = deliverySpec.amount().getCurrency();
            if (deliveryCurrency != null && !deliveryCurrency.isBlank() && !deliveryCurrency.equalsIgnoreCase(currency)) {
                throw new IllegalArgumentException("Delivery currency mismatch");
            }
        }
        Money totalMoney = Money.of(itemsTotal + deliveryAmount, currency);
        String baseKey = "order-cart-" + cartId;

        UUID customerId = customerIdOverride != null
                ? customerIdOverride
                : cart.getCustomerId();
        if (customerId == null) {
            throw new IllegalArgumentException("Customer is required to create an order");
        }
        Order order = new Order(customerId, "PENDING", totalMoney);
        if (deliverySpec != null) {
            order.setDeliveryAmount(deliverySpec.amount());
            order.setDeliveryProvider(deliverySpec.provider());
            order.setDeliveryMethod(deliverySpec.method());
            order.setDeliveryAddress(deliverySpec.address());
            order.setDeliveryPickupPointId(deliverySpec.pickupPointId());
            order.setDeliveryPickupPointName(deliverySpec.pickupPointName());
            order.setDeliveryIntervalFrom(deliverySpec.intervalFrom());
            order.setDeliveryIntervalTo(deliverySpec.intervalTo());
            order.setDeliveryOfferId(deliverySpec.offerId());
            order.setDeliveryRequestId(deliverySpec.requestId());
            order.setDeliveryStatus(deliverySpec.status());
        }
        order.setReceiptEmail(receiptEmail);
        order.setPublicToken(generatePublicToken());
        order.setManagerSubject(managerSubject);

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
        Order order = orderRepository.findWithItemsById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found: " + orderId));
        String previousStatus = order.getStatus();
        order.setStatus(status);
        orderRepository.save(order);
        if (shouldRestock(previousStatus, status)) {
            restockOrderItems(order, "ORDER_STATUS_" + status, "restock-" + orderId + "-" + status.toLowerCase());
        }
    }

    public void restockOrderItems(UUID orderId, String reason, String idempotencyPrefix) {
        if (orderId == null) {
            return;
        }
        Order order = orderRepository.findWithItemsById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found: " + orderId));
        restockOrderItems(order, reason, idempotencyPrefix);
    }

    private void restockOrderItems(Order order, String reason, String idempotencyPrefix) {
        if (order == null || order.getItems() == null || order.getItems().isEmpty()) {
            return;
        }
        String baseKey = idempotencyPrefix != null && !idempotencyPrefix.isBlank()
                ? idempotencyPrefix
                : "restock-" + order.getId();
        for (OrderItem item : order.getItems()) {
            String key = baseKey + "-" + item.getVariantId();
            inventoryService.adjustStock(item.getVariantId(), item.getQuantity(), key, reason);
        }
    }

    private boolean shouldRestock(String previousStatus, String nextStatus) {
        if (nextStatus == null) {
            return false;
        }
        if (!"CANCELLED".equalsIgnoreCase(nextStatus) && !"REFUNDED".equalsIgnoreCase(nextStatus)) {
            return false;
        }
        if (previousStatus == null) {
            return true;
        }
        return !previousStatus.equalsIgnoreCase(nextStatus);
    }

    public List<Order> getOrdersByCustomerId(UUID customerId) {
        return orderRepository.findByCustomerId(customerId);
    }

    public List<Order> getAllOrders() {
        return orderRepository.findAllByOrderByOrderDateDesc();
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

    public Order updateDeliveryStatus(UUID orderId,
                                      String status,
                                      OffsetDateTime intervalFrom,
                                      OffsetDateTime intervalTo) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found: " + orderId));
        if (status != null && !status.isBlank()) {
            order.setDeliveryStatus(status);
        }
        if (intervalFrom != null) {
            order.setDeliveryIntervalFrom(intervalFrom);
        }
        if (intervalTo != null) {
            order.setDeliveryIntervalTo(intervalTo);
        }
        return orderRepository.save(order);
    }

    private String generatePublicToken() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    public record DeliverySpec(Money amount,
                               String provider,
                               String method,
                               String address,
                               String pickupPointId,
                               String pickupPointName,
                               OffsetDateTime intervalFrom,
                               OffsetDateTime intervalTo,
                               String offerId,
                               String requestId,
                               String status) {}
}
