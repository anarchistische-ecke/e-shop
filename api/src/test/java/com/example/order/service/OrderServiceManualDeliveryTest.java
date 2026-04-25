package com.example.order.service;

import com.example.cart.domain.Cart;
import com.example.cart.domain.CartItem;
import com.example.cart.service.CartPricingSummary;
import com.example.cart.service.CartService;
import com.example.catalog.domain.Product;
import com.example.catalog.domain.ProductVariant;
import com.example.catalog.repository.ProductVariantRepository;
import com.example.catalog.service.InventoryService;
import com.example.common.domain.Money;
import com.example.order.domain.Order;
import com.example.order.repository.OrderCheckoutAttemptRepository;
import com.example.order.repository.OrderItemRepository;
import com.example.order.repository.OrderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceManualDeliveryTest {

    @Test
    void createOrderFromCartStoresManualContactAndKeepsTotalToProductsOnly() {
        OrderRepository orderRepository = mock(OrderRepository.class);
        OrderItemRepository orderItemRepository = mock(OrderItemRepository.class);
        CartService cartService = mock(CartService.class);
        InventoryService inventoryService = mock(InventoryService.class);
        ProductVariantRepository variantRepository = mock(ProductVariantRepository.class);
        OrderCheckoutAttemptRepository checkoutAttemptRepository = mock(OrderCheckoutAttemptRepository.class);
        OrderService service = new OrderService(
                orderRepository,
                orderItemRepository,
                cartService,
                inventoryService,
                variantRepository,
                checkoutAttemptRepository
        );

        UUID cartId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID variantId = UUID.randomUUID();
        Cart cart = new Cart(customerId);
        cart.addItem(new CartItem(variantId, 2, Money.of(210000, "RUB")));
        when(cartService.getCartById(cartId)).thenReturn(cart);
        when(cartService.calculateCartPricing(cartId)).thenReturn(new CartPricingSummary(
                Money.of(420000, "RUB"),
                Money.of(420000, "RUB"),
                Money.of(420000, "RUB"),
                Money.of(0, "RUB"),
                Money.of(0, "RUB"),
                Money.of(0, "RUB"),
                Money.of(0, "RUB"),
                Money.of(0, "RUB"),
                Money.of(420000, "RUB"),
                null,
                null,
                null,
                null,
                false,
                List.of(new CartPricingSummary.CartPricingLine(
                        variantId,
                        2,
                        Money.of(210000, "RUB"),
                        Money.of(210000, "RUB"),
                        Money.of(420000, "RUB"),
                        Money.of(420000, "RUB"),
                        Money.of(0, "RUB"),
                        Money.of(0, "RUB"),
                        Money.of(420000, "RUB"),
                        false,
                        null,
                        null,
                        null
                ))
        ));

        Product product = new Product("Сатиновый комплект Sand", "desc", "satin-sand");
        ProductVariant variant = new ProductVariant("SKU-1", "200x220", Money.of(210000, "RUB"), 4);
        variant.setProduct(product);
        when(variantRepository.findWithProductById(variantId)).thenReturn(Optional.of(variant));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order order = invocation.getArgument(0);
            order.setId(UUID.randomUUID());
            return order;
        });

        Order order = service.createOrderFromCart(
                cartId,
                customerId,
                "buyer@example.test",
                null,
                new OrderService.ContactSpec("Иван Петров", "+79990000000", "Москва, Тестовая улица, 1")
        );

        assertEquals(420000, order.getTotalAmount().getAmount());
        assertEquals("RUB", order.getTotalAmount().getCurrency());
        assertNull(order.getDeliveryAmount());
        assertNull(order.getDeliveryProvider());
        assertNull(order.getDeliveryMethod());
        assertNull(order.getDeliveryRequestId());
        assertNull(order.getDeliveryOfferId());
        assertEquals("Иван Петров", order.getContactName());
        assertEquals("+79990000000", order.getContactPhone());
        assertEquals("Москва, Тестовая улица, 1", order.getHomeAddress());

        verify(cartService).clearCart(cartId);
        verify(inventoryService).adjustStock(variantId, -2, "order-cart-" + cartId + "-" + variantId, "ORDER");
    }
}
