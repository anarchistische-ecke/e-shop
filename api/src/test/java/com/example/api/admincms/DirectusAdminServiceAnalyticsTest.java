package com.example.api.admincms;

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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DirectusAdminServiceAnalyticsTest {

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
    void managerAnalytics_matchesDirectusEmailViaPaymentLink() {
        UUID ownOrderId = UUID.randomUUID();
        Order ownOrder = order(ownOrderId, "keycloak-subject-1", "PAID", 10_000);
        Order otherOrder = order(UUID.randomUUID(), "keycloak-subject-2", "PAID", 20_000);
        ManagerPaymentLink ownLink = paymentLink(ownOrderId, "keycloak-subject-1", "manager@example.test");

        when(orderRepository.findAllByOrderByOrderDateDesc()).thenReturn(List.of(ownOrder, otherOrder));
        when(managerPaymentLinkRepository.findAll()).thenReturn(List.of(ownLink));

        DirectusAdminModels.ManagerAnalyticsResponse response = service.managerAnalytics(null, null, "manager@example.test");

        assertThat(response.rows()).hasSize(1);
        assertThat(response.rows().get(0).managerSubject()).isEqualTo("keycloak-subject-1");
        assertThat(response.rows().get(0).paidAmount().getAmount()).isEqualTo(10_000);
    }

    @Test
    void paymentLinkAnalytics_matchesManagerEmailAndHidesOtherManagers() {
        UUID ownOrderId = UUID.randomUUID();
        UUID otherOrderId = UUID.randomUUID();
        ManagerPaymentLink ownLink = paymentLink(ownOrderId, "keycloak-subject-1", "manager@example.test");
        ManagerPaymentLink otherLink = paymentLink(otherOrderId, "keycloak-subject-2", "other@example.test");

        when(managerPaymentLinkRepository.findByCreatedAtBetweenOrderByCreatedAtDesc(any(), any()))
                .thenReturn(List.of(ownLink, otherLink));
        when(orderRepository.findById(ownOrderId)).thenReturn(Optional.of(order(ownOrderId, "keycloak-subject-1", "PAID", 10_000)));

        DirectusAdminModels.PaymentLinkAnalyticsResponse response = service.paymentLinkAnalytics(null, null, "manager@example.test");

        assertThat(response.sent()).isEqualTo(1);
        assertThat(response.paid()).isEqualTo(1);
        assertThat(response.rows()).hasSize(1);
        assertThat(response.rows().get(0).managerEmail()).isEqualTo("manager@example.test");
    }

    private Order order(UUID id, String managerSubject, String status, long amount) {
        Order order = new Order(UUID.randomUUID(), status, Money.of(amount, "RUB"));
        order.setId(id);
        order.setManagerSubject(managerSubject);
        order.setOrderDate(OffsetDateTime.now());
        return order;
    }

    private ManagerPaymentLink paymentLink(UUID orderId, String managerSubject, String managerEmail) {
        ManagerPaymentLink link = new ManagerPaymentLink();
        link.setId(UUID.randomUUID());
        link.setOrderId(orderId);
        link.setManagerSubject(managerSubject);
        link.setManagerEmail(managerEmail);
        link.setStatus("SENT");
        link.setSentAt(OffsetDateTime.now());
        return link;
    }
}
