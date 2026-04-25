package com.example.api.admincms;

import com.example.api.admincms.DirectusAdminModels.OrderRefundLineRequest;
import com.example.api.admincms.DirectusAdminModels.OrderRefundRequest;
import com.example.api.catalog.DirectusBridgeSecurity;
import com.example.catalog.repository.ProductRepository;
import com.example.catalog.repository.ProductVariantRepository;
import com.example.catalog.service.CatalogService;
import com.example.catalog.service.InventoryService;
import com.example.common.domain.Money;
import com.example.order.domain.Order;
import com.example.order.repository.OrderRepository;
import com.example.order.service.OrderService;
import com.example.payment.service.PaymentService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DirectusAdminServiceRefundTest {
    @Mock
    private OrderRepository orderRepository;
    @Mock
    private OrderService orderService;
    @Mock
    private OrderStatusHistoryRepository orderStatusHistoryRepository;
    @Mock
    private DirectusAdminRoleGuard roleGuard;
    @Mock
    private CatalogueImportJobRepository importJobRepository;
    @Mock
    private CatalogueImportRowRepository importRowRepository;
    @Mock
    private PromotionRepository promotionRepository;
    @Mock
    private PromoCodeRepository promoCodeRepository;
    @Mock
    private TaxConfigurationRepository taxConfigurationRepository;
    @Mock
    private ManagerPaymentLinkRepository managerPaymentLinkRepository;
    @Mock
    private StockAlertSettingsRepository stockAlertSettingsRepository;
    @Mock
    private ProductRepository productRepository;
    @Mock
    private ProductVariantRepository variantRepository;
    @Mock
    private CatalogService catalogService;
    @Mock
    private InventoryService inventoryService;
    @Mock
    private PaymentService paymentService;

    private DirectusAdminService service;

    @BeforeEach
    void setUp() {
        service = new DirectusAdminService(
                orderRepository,
                orderService,
                orderStatusHistoryRepository,
                roleGuard,
                importJobRepository,
                importRowRepository,
                promotionRepository,
                promoCodeRepository,
                taxConfigurationRepository,
                managerPaymentLinkRepository,
                stockAlertSettingsRepository,
                productRepository,
                variantRepository,
                catalogService,
                inventoryService,
                new ObjectMapper(),
                null,
                null,
                null,
                paymentService
        );
    }

    @Test
    void refundOrderRejectsNonAdminBeforeCallingPaymentService() {
        UUID orderId = UUID.randomUUID();
        DirectusBridgeSecurity.DirectusBridgePrincipal principal = principal("manager-role");
        when(roleGuard.isAdmin(principal)).thenReturn(false);

        assertThatThrownBy(() -> service.refundOrder(orderId, new OrderRefundRequest(List.of()), principal))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Only administrators");
        verify(paymentService, never()).refundYooKassaPayment(any(), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void refundOrderMapsLinesAndWritesAuditEventForAdmin() {
        UUID orderId = UUID.randomUUID();
        UUID orderItemId = UUID.randomUUID();
        DirectusBridgeSecurity.DirectusBridgePrincipal principal = principal("admin");
        Order order = new Order(UUID.randomUUID(), "PAID", Money.of(420000, "RUB"));
        order.setId(orderId);
        when(roleGuard.isAdmin(principal)).thenReturn(true);
        when(orderService.findById(orderId)).thenReturn(order, order);
        when(orderStatusHistoryRepository.findByOrderIdOrderByCreatedAtAsc(orderId)).thenReturn(List.of());

        service.refundOrder(
                orderId,
                new OrderRefundRequest(List.of(new OrderRefundLineRequest(orderItemId, 1, 210000L))),
                principal
        );

        ArgumentCaptor<List<PaymentService.RefundLineRequest>> lineCaptor = ArgumentCaptor.forClass(List.class);
        verify(paymentService).refundYooKassaPayment(eq(orderId), lineCaptor.capture());
        assertThat(lineCaptor.getValue()).hasSize(1);
        assertThat(lineCaptor.getValue().getFirst().orderItemId()).isEqualTo(orderItemId);
        assertThat(lineCaptor.getValue().getFirst().quantity()).isEqualTo(1);
        assertThat(lineCaptor.getValue().getFirst().amount()).isEqualTo(210000L);

        ArgumentCaptor<OrderStatusHistory> eventCaptor = ArgumentCaptor.forClass(OrderStatusHistory.class);
        verify(orderStatusHistoryRepository).save(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getNote()).isEqualTo("payment-refund");
        assertThat(eventCaptor.getValue().getActor()).isEqualTo("admin@example.test");
    }

    private DirectusBridgeSecurity.DirectusBridgePrincipal principal(String role) {
        return new DirectusBridgeSecurity.DirectusBridgePrincipal(
                "directus-user-1",
                "admin@example.test",
                null,
                role,
                role
        );
    }
}
