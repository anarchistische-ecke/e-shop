package com.example.api.admincms;

import com.example.api.catalog.DirectusBridgeSecurity;
import com.example.catalog.repository.ProductRepository;
import com.example.catalog.repository.ProductVariantRepository;
import com.example.catalog.service.CatalogService;
import com.example.catalog.service.InventoryService;
import com.example.common.domain.Money;
import com.example.order.domain.Order;
import com.example.order.repository.OrderRepository;
import com.example.order.service.OrderService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DirectusAdminServiceOrderWorkflowTest {

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
                new ObjectMapper()
        );
    }

    @Test
    void claimOrder_assignsDirectusManagerAndWritesAuditEvent() {
        UUID orderId = UUID.randomUUID();
        Order unassigned = order(orderId, "PENDING", null, null, null);
        Order assigned = order(orderId, "PENDING", "manager@example.test", "manager-user-1", "manager@example.test");
        assigned.setManagerClaimedAt(OffsetDateTime.now());
        DirectusBridgeSecurity.DirectusBridgePrincipal principal = managerPrincipal("manager-user-1", "manager@example.test");

        when(orderService.findById(orderId)).thenReturn(unassigned, assigned);
        when(roleGuard.isManager(principal)).thenReturn(true);
        when(orderRepository.claimDirectusManager(any(), any(), any(), any(), any())).thenReturn(1);
        when(orderStatusHistoryRepository.findByOrderIdOrderByCreatedAtAsc(orderId)).thenReturn(List.of());

        DirectusAdminModels.OrderDetail detail = service.claimOrder(orderId, principal);

        assertThat(detail.order().getManagerSubject()).isEqualTo("manager@example.test");
        assertThat(detail.order().getManagerEmail()).isEqualTo("manager@example.test");
        assertThat(detail.order().getManagerDirectusUserId()).isEqualTo("manager-user-1");
        assertThat(detail.order().getManagerClaimedAt()).isNotNull();

        verify(orderRepository).claimDirectusManager(
                any(UUID.class),
                any(String.class),
                any(String.class),
                any(String.class),
                any(OffsetDateTime.class)
        );

        ArgumentCaptor<OrderStatusHistory> eventCaptor = ArgumentCaptor.forClass(OrderStatusHistory.class);
        verify(orderStatusHistoryRepository).save(eventCaptor.capture());
        OrderStatusHistory event = eventCaptor.getValue();
        assertThat(event.getOrderId()).isEqualTo(orderId);
        assertThat(event.getPreviousStatus()).isEqualTo("PENDING");
        assertThat(event.getNextStatus()).isEqualTo("PENDING");
        assertThat(event.getActor()).isEqualTo("manager@example.test");
        assertThat(event.getActorRole()).isEqualTo("manager-role");
        assertThat(event.getNote()).isEqualTo("claimed");
    }

    @Test
    void claimOrder_bySameDirectusUserIsIdempotent() {
        UUID orderId = UUID.randomUUID();
        Order order = order(orderId, "PENDING", "manager@example.test", "manager-user-1", "manager@example.test");
        DirectusBridgeSecurity.DirectusBridgePrincipal principal = managerPrincipal("manager-user-1", "manager@example.test");

        when(orderService.findById(orderId)).thenReturn(order);
        when(roleGuard.isManager(principal)).thenReturn(true);
        when(orderStatusHistoryRepository.findByOrderIdOrderByCreatedAtAsc(orderId)).thenReturn(List.of());

        DirectusAdminModels.OrderDetail detail = service.claimOrder(orderId, principal);

        assertThat(detail.order()).isSameAs(order);
        verify(orderRepository, never()).save(any());
        verify(orderStatusHistoryRepository, never()).save(any());
    }

    @Test
    void claimOrder_whenConcurrentClaimWinsReturnsConflict() {
        UUID orderId = UUID.randomUUID();
        Order unassigned = order(orderId, "PENDING", null, null, null);
        Order alreadyClaimed = order(orderId, "PENDING", "other@example.test", "other-user", "other@example.test");
        DirectusBridgeSecurity.DirectusBridgePrincipal principal = managerPrincipal("manager-user-1", "manager@example.test");

        when(orderService.findById(orderId)).thenReturn(unassigned, alreadyClaimed);
        when(roleGuard.isManager(principal)).thenReturn(true);
        when(orderRepository.claimDirectusManager(any(), any(), any(), any(), any())).thenReturn(0);

        assertThatThrownBy(() -> service.claimOrder(orderId, principal))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("other@example.test");

        verify(orderStatusHistoryRepository, never()).save(any());
    }

    @Test
    void claimOrder_byDifferentManagerReturnsConflict() {
        UUID orderId = UUID.randomUUID();
        Order order = order(orderId, "PENDING", "other@example.test", "other-user", "other@example.test");
        DirectusBridgeSecurity.DirectusBridgePrincipal principal = managerPrincipal("manager-user-1", "manager@example.test");

        when(orderService.findById(orderId)).thenReturn(order);
        when(roleGuard.isManager(principal)).thenReturn(true);

        assertThatThrownBy(() -> service.claimOrder(orderId, principal))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("other@example.test");

        verify(orderRepository, never()).save(any());
        verify(orderStatusHistoryRepository, never()).save(any());
    }

    @Test
    void searchOrders_forManagerReturnsOwnAndUnassignedOnly() {
        Order own = order(UUID.randomUUID(), "PENDING", "manager@example.test", "manager-user-1", "manager@example.test");
        Order unassigned = order(UUID.randomUUID(), "PENDING", null, null, null);
        Order other = order(UUID.randomUUID(), "PENDING", "other@example.test", "other-user", "other@example.test");
        DirectusBridgeSecurity.DirectusBridgePrincipal principal = managerPrincipal("manager-user-1", "manager@example.test");

        when(roleGuard.isManager(principal)).thenReturn(true);
        when(orderRepository.findAllByOrderByOrderDateDesc()).thenReturn(List.of(own, unassigned, other));

        DirectusAdminModels.OrderSearchResponse response = service.searchOrders(null, null, null, null, null, principal);

        assertThat(response.items())
                .extracting(DirectusAdminModels.OrderSummary::id)
                .containsExactly(own.getId(), unassigned.getId());
    }

    @Test
    void claimOrder_rejectsPickerRole() {
        UUID orderId = UUID.randomUUID();
        Order order = order(orderId, "PAID", null, null, null);
        DirectusBridgeSecurity.DirectusBridgePrincipal principal = pickerPrincipal();

        when(orderService.findById(orderId)).thenReturn(order);
        when(roleGuard.isPicker(principal)).thenReturn(true);

        assertThatThrownBy(() -> service.claimOrder(orderId, principal))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("cannot claim");

        verify(orderRepository, never()).save(any());
        verify(orderStatusHistoryRepository, never()).save(any());
    }

    @Test
    void updateOrderStatus_allowsPickerQueueTransitionButRejectsCancellation() {
        UUID orderId = UUID.randomUUID();
        Order before = order(orderId, "PAID", null, null, null);
        Order updated = order(orderId, "PROCESSING", null, null, null);
        DirectusBridgeSecurity.DirectusBridgePrincipal principal = pickerPrincipal();

        when(orderService.findById(orderId)).thenReturn(before);
        when(roleGuard.isPicker(principal)).thenReturn(true);
        when(orderService.updateOrderStatus(orderId, "PROCESSING")).thenReturn(updated);
        when(orderStatusHistoryRepository.findByOrderIdOrderByCreatedAtAsc(orderId)).thenReturn(List.of());

        DirectusAdminModels.OrderDetail detail = service.updateOrderStatus(orderId, "processing", null, principal);

        assertThat(detail.order().getStatus()).isEqualTo("PROCESSING");

        assertThatThrownBy(() -> service.updateOrderStatus(orderId, "CANCELLED", null, principal))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Picker can only advance");
    }

    @Test
    void updateOrderStatus_rejectsUnknownStatus() {
        UUID orderId = UUID.randomUUID();
        Order before = order(orderId, "PAID", "manager@example.test", "manager-user-1", "manager@example.test");
        DirectusBridgeSecurity.DirectusBridgePrincipal principal = managerPrincipal("manager-user-1", "manager@example.test");

        when(orderService.findById(orderId)).thenReturn(before);

        assertThatThrownBy(() -> service.updateOrderStatus(orderId, "lost", null, principal))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported order status");

        verify(orderService, never()).updateOrderStatus(any(), any());
    }

    @Test
    void clearOrderClaim_isAdminOnlyAndClearsAssignment() {
        UUID orderId = UUID.randomUUID();
        Order order = order(orderId, "PENDING", "manager@example.test", "manager-user-1", "manager@example.test");
        DirectusBridgeSecurity.DirectusBridgePrincipal admin = new DirectusBridgeSecurity.DirectusBridgePrincipal(
                "admin-user-1",
                "admin@example.test",
                "admin@example.test",
                "admin",
                "admin"
        );

        when(roleGuard.isAdmin(admin)).thenReturn(true);
        when(orderService.findById(orderId)).thenReturn(order);
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(orderStatusHistoryRepository.findByOrderIdOrderByCreatedAtAsc(orderId)).thenReturn(List.of());

        DirectusAdminModels.OrderDetail detail = service.clearOrderClaim(orderId, admin);

        assertThat(detail.order().getManagerSubject()).isNull();
        assertThat(detail.order().getManagerEmail()).isNull();
        assertThat(detail.order().getManagerDirectusUserId()).isNull();
        assertThat(detail.order().getManagerClaimedAt()).isNull();
    }

    private Order order(UUID id, String status, String managerSubject, String managerDirectusUserId, String managerEmail) {
        Order order = new Order(UUID.randomUUID(), status, Money.of(10_000, "RUB"));
        order.setId(id);
        order.setReceiptEmail("customer@example.test");
        order.setOrderDate(OffsetDateTime.now());
        order.setManagerSubject(managerSubject);
        order.setManagerDirectusUserId(managerDirectusUserId);
        order.setManagerEmail(managerEmail);
        return order;
    }

    private DirectusBridgeSecurity.DirectusBridgePrincipal managerPrincipal(String userId, String email) {
        return new DirectusBridgeSecurity.DirectusBridgePrincipal(userId, email, email, "manager-role", "manager-role");
    }

    private DirectusBridgeSecurity.DirectusBridgePrincipal pickerPrincipal() {
        return new DirectusBridgeSecurity.DirectusBridgePrincipal(
                "picker-user-1",
                "picker@example.test",
                "picker@example.test",
                "picker-role",
                "picker-role"
        );
    }
}
