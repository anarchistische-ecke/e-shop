package com.example.api.admincms;

import com.example.api.admincms.DirectusAdminModels.RmaDecisionRequest;
import com.example.api.admincms.DirectusAdminModels.RmaRequestCreateRequest;
import com.example.api.catalog.DirectusBridgeSecurity;
import com.example.api.notification.NotificationOrchestrator;
import com.example.catalog.repository.ProductRepository;
import com.example.catalog.repository.ProductVariantRepository;
import com.example.catalog.service.CatalogService;
import com.example.catalog.service.InventoryService;
import com.example.common.domain.Money;
import com.example.order.domain.Order;
import com.example.order.domain.RmaRequest;
import com.example.order.domain.RmaStatus;
import com.example.order.repository.OrderRepository;
import com.example.order.repository.RmaRequestRepository;
import com.example.order.service.OrderService;
import com.example.shipment.repository.ShipmentRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DirectusAdminServiceRmaTest {
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
    private RmaRequestRepository rmaRequestRepository;
    @Mock
    private NotificationOrchestrator notificationOrchestrator;
    @Mock
    private ShipmentRepository shipmentRepository;

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
                rmaRequestRepository,
                notificationOrchestrator,
                shipmentRepository
        );
    }

    @Test
    void createRmaRequestTiesRequestToReadableOrder() {
        UUID orderId = UUID.randomUUID();
        Order order = order(orderId, null, null);
        DirectusBridgeSecurity.DirectusBridgePrincipal principal = managerPrincipal("manager-user-1", "manager@example.test");
        when(orderService.findById(orderId)).thenReturn(order);
        when(roleGuard.isManager(principal)).thenReturn(true);
        when(rmaRequestRepository.existsByRmaNumber(any())).thenReturn(false);
        when(rmaRequestRepository.save(any(RmaRequest.class))).thenAnswer(invocation -> {
            RmaRequest request = invocation.getArgument(0);
            request.setId(UUID.randomUUID());
            request.setCreatedAt(OffsetDateTime.now());
            request.setUpdatedAt(OffsetDateTime.now());
            return request;
        });

        DirectusAdminModels.RmaRequestView response = service.createRmaRequest(
                new RmaRequestCreateRequest(orderId, "Брак", "Возврат денег"),
                principal
        );

        assertThat(response.orderId()).isEqualTo(orderId);
        assertThat(response.status()).isEqualTo("REQUESTED");
        assertThat(response.customerEmail()).isEqualTo("customer@example.test");
    }

    @Test
    void decideRmaRequestEnqueuesDecisionNotificationOnChange() {
        UUID orderId = UUID.randomUUID();
        UUID rmaId = UUID.randomUUID();
        Order order = order(orderId, "manager@example.test", "manager-user-1");
        RmaRequest rma = rma(rmaId, orderId, RmaStatus.REQUESTED, null, 0);
        DirectusBridgeSecurity.DirectusBridgePrincipal principal = managerPrincipal("manager-user-1", "manager@example.test");
        when(rmaRequestRepository.findById(rmaId)).thenReturn(Optional.of(rma));
        when(orderService.findById(orderId)).thenReturn(order);
        when(roleGuard.isManager(principal)).thenReturn(true);
        when(rmaRequestRepository.save(any(RmaRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));

        DirectusAdminModels.RmaRequestView response = service.decideRmaRequest(
                rmaId,
                new RmaDecisionRequest("APPROVED", "Возврат одобрен"),
                principal
        );

        assertThat(response.status()).isEqualTo("APPROVED");
        assertThat(response.decisionVersion()).isEqualTo(1);
        verify(notificationOrchestrator).rmaDecision(
                eq(order),
                eq(rmaId),
                eq("RMA-1"),
                eq(1),
                eq("APPROVED"),
                eq("Возврат одобрен")
        );
    }

    @Test
    void repeatedSameRmaDecisionDoesNotSendDuplicateNotification() {
        UUID orderId = UUID.randomUUID();
        UUID rmaId = UUID.randomUUID();
        Order order = order(orderId, "manager@example.test", "manager-user-1");
        RmaRequest rma = rma(rmaId, orderId, RmaStatus.APPROVED, "Возврат одобрен", 1);
        DirectusBridgeSecurity.DirectusBridgePrincipal principal = managerPrincipal("manager-user-1", "manager@example.test");
        when(rmaRequestRepository.findById(rmaId)).thenReturn(Optional.of(rma));
        when(orderService.findById(orderId)).thenReturn(order);
        when(roleGuard.isManager(principal)).thenReturn(true);

        service.decideRmaRequest(rmaId, new RmaDecisionRequest("APPROVED", "Возврат одобрен"), principal);

        verify(rmaRequestRepository, never()).save(any());
        verify(notificationOrchestrator, never()).rmaDecision(any(), any(), any(), anyInt(), any(), any());
    }

    @Test
    void managerCannotDecideRmaForUnclaimedOrder() {
        UUID orderId = UUID.randomUUID();
        UUID rmaId = UUID.randomUUID();
        Order order = order(orderId, null, null);
        RmaRequest rma = rma(rmaId, orderId, RmaStatus.REQUESTED, null, 0);
        DirectusBridgeSecurity.DirectusBridgePrincipal principal = managerPrincipal("manager-user-1", "manager@example.test");
        when(rmaRequestRepository.findById(rmaId)).thenReturn(Optional.of(rma));
        when(orderService.findById(orderId)).thenReturn(order);
        when(roleGuard.isManager(principal)).thenReturn(true);

        assertThatThrownBy(() -> service.decideRmaRequest(rmaId, new RmaDecisionRequest("REJECTED", "Нет фото"), principal))
                .isInstanceOf(AccessDeniedException.class);
    }

    private Order order(UUID id, String managerEmail, String managerDirectusUserId) {
        Order order = new Order(UUID.randomUUID(), "PAID", Money.of(10_000, "RUB"));
        order.setId(id);
        order.setReceiptEmail("customer@example.test");
        order.setManagerEmail(managerEmail);
        order.setManagerSubject(managerEmail);
        order.setManagerDirectusUserId(managerDirectusUserId);
        return order;
    }

    private RmaRequest rma(UUID id, UUID orderId, RmaStatus status, String comment, int decisionVersion) {
        RmaRequest rma = new RmaRequest();
        rma.setId(id);
        rma.setRmaNumber("RMA-1");
        rma.setOrderId(orderId);
        rma.setCustomerEmail("customer@example.test");
        rma.setStatus(status);
        rma.setManagerComment(comment);
        rma.setDecisionVersion(decisionVersion);
        return rma;
    }

    private DirectusBridgeSecurity.DirectusBridgePrincipal managerPrincipal(String userId, String email) {
        return new DirectusBridgeSecurity.DirectusBridgePrincipal(userId, email, email, "manager-role", "manager-role");
    }
}
