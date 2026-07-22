package com.example.payment.service;

import com.example.api.admincms.ActiveFiscalConfigurationProvider;
import com.example.api.admincms.CatalogueImportJobRepository;
import com.example.api.admincms.CatalogueImportRowRepository;
import com.example.api.admincms.DirectusAdminModels.TaxConfigurationRequest;
import com.example.api.admincms.DirectusAdminModels.TaxConfigurationView;
import com.example.api.admincms.DirectusAdminRoleGuard;
import com.example.api.admincms.DirectusAdminService;
import com.example.api.admincms.ManagerPaymentLinkRepository;
import com.example.api.admincms.OrderStatusHistoryRepository;
import com.example.api.admincms.PromoCodeRepository;
import com.example.api.admincms.PromotionRepository;
import com.example.api.admincms.StockAlertSettingsRepository;
import com.example.api.admincms.TaxConfiguration;
import com.example.api.admincms.TaxConfigurationRepository;
import com.example.api.notification.NotificationOrchestrator;
import com.example.catalog.repository.ProductRepository;
import com.example.catalog.repository.ProductVariantRepository;
import com.example.catalog.service.CatalogService;
import com.example.catalog.service.InventoryService;
import com.example.common.domain.Money;
import com.example.customer.repository.CustomerRepository;
import com.example.order.domain.Order;
import com.example.order.domain.OrderItem;
import com.example.order.repository.OrderRepository;
import com.example.order.repository.RmaRequestRepository;
import com.example.order.service.OrderService;
import com.example.payment.domain.Payment;
import com.example.payment.repository.PaymentRefundItemRepository;
import com.example.payment.repository.PaymentRefundRepository;
import com.example.payment.repository.PaymentRepository;
import com.example.payment.repository.SavedPaymentMethodRepository;
import com.example.shipment.repository.ShipmentRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentServiceDirectusTaxReceiptTest {

    private final Map<UUID, TaxConfiguration> taxRows = new LinkedHashMap<>();
    private final Map<UUID, Order> orders = new LinkedHashMap<>();

    private TaxConfigurationRepository taxConfigurationRepository;
    private OrderRepository orderRepository;
    private PaymentRepository paymentRepository;
    private YooKassaClient yooKassaClient;
    private DirectusAdminService adminService;
    private PaymentService paymentService;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        taxConfigurationRepository = mock(TaxConfigurationRepository.class);
        stubTaxRepository();

        orderRepository = mock(OrderRepository.class);
        paymentRepository = mock(PaymentRepository.class);
        yooKassaClient = mock(YooKassaClient.class);

        when(orderRepository.findWithItemsById(any(UUID.class))).thenAnswer(invocation ->
                Optional.ofNullable(orders.get(invocation.getArgument(0)))
        );
        when(paymentRepository.findTopByOrderIdOrderByPaymentDateDesc(any(UUID.class))).thenReturn(Optional.empty());
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> {
            Payment payment = invocation.getArgument(0);
            if (payment.getId() == null) {
                payment.setId(UUID.randomUUID());
            }
            return payment;
        });
        AtomicInteger paymentCounter = new AtomicInteger();
        when(yooKassaClient.createPayment(any(YooKassaClient.CreatePaymentRequest.class), anyString()))
                .thenAnswer(invocation -> {
                    YooKassaClient.CreatePaymentResponse response = new YooKassaClient.CreatePaymentResponse();
                    response.id = "pay-tax-" + paymentCounter.incrementAndGet();
                    response.status = "pending";
                    response.test = true;
                    response.paid = false;
                    response.confirmation = YooKassaClient.Confirmation.redirect("https://yookassa.test/confirm");
                    response.receiptRegistration = "pending";
                    return response;
                });

        adminService = new DirectusAdminService(
                orderRepository,
                mock(OrderService.class),
                mock(OrderStatusHistoryRepository.class),
                mock(DirectusAdminRoleGuard.class),
                mock(CatalogueImportJobRepository.class),
                mock(CatalogueImportRowRepository.class),
                mock(PromotionRepository.class),
                mock(PromoCodeRepository.class),
                taxConfigurationRepository,
                mock(ManagerPaymentLinkRepository.class),
                mock(StockAlertSettingsRepository.class),
                mock(ProductRepository.class),
                mock(ProductVariantRepository.class),
                mock(CatalogService.class),
                mock(InventoryService.class),
                new ObjectMapper(),
                mock(RmaRequestRepository.class),
                mock(NotificationOrchestrator.class),
                mock(ShipmentRepository.class),
                null
        );

        paymentService = new PaymentService(
                paymentRepository,
                mock(PaymentRefundRepository.class),
                provider(null),
                mock(SavedPaymentMethodRepository.class),
                orderRepository,
                provider(mock(CustomerRepository.class)),
                provider(yooKassaClient),
                provider(null),
                provider(new ActiveFiscalConfigurationProvider(taxConfigurationRepository)),
                provider(null)
        );
    }

    @Test
    void taxTabSettingsPersistAndNextYooKassaReceiptUsesNewActiveConfig() {
        TaxConfigurationView initial = adminService.saveTaxConfiguration(null, taxRequest("ОСН 0", 1, 1, true));
        Order firstOrder = order(11234);
        orders.put(firstOrder.getId(), firstOrder);

        paymentService.createYooKassaPayment(
                firstOrder.getId(),
                "buyer@example.test",
                "https://example.test/return",
                "order-key-1",
                false,
                null,
                "REDIRECT"
        );

        TaxConfigurationView updated = adminService.saveTaxConfiguration(null, taxRequest("УСН 10", 6, 2, true));
        Order secondOrder = order(11234);
        orders.put(secondOrder.getId(), secondOrder);

        paymentService.createYooKassaPayment(
                secondOrder.getId(),
                "buyer@example.test",
                "https://example.test/return",
                "order-key-2",
                false,
                null,
                "REDIRECT"
        );

        assertThat(adminService.getTaxConfiguration(initial.id()).active()).isFalse();
        assertThat(adminService.activeTaxConfigurationView()).contains(updated);

        ArgumentCaptor<YooKassaClient.CreatePaymentRequest> requestCaptor =
                ArgumentCaptor.forClass(YooKassaClient.CreatePaymentRequest.class);
        verify(yooKassaClient).createPayment(requestCaptor.capture(), eq("order-key-1"));
        verify(yooKassaClient).createPayment(requestCaptor.capture(), eq("order-key-2"));
        List<YooKassaClient.CreatePaymentRequest> requests = requestCaptor.getAllValues();

        YooKassaClient.CreatePaymentRequest first = requests.get(0);
        assertThat(first.receipt.taxSystemCode).isEqualTo(1);
        assertThat(first.receipt.items).allSatisfy(item -> assertThat(item.vatCode).isEqualTo(1));

        YooKassaClient.CreatePaymentRequest second = requests.get(1);
        assertThat(second.amount.value).isEqualTo("112.34");
        assertThat(second.receipt.customer.email).isEqualTo("buyer@example.test");
        assertThat(second.receipt.customer.phone).isEqualTo("+79990000000");
        assertThat(second.receipt.taxSystemCode).isEqualTo(6);
        assertThat(second.receipt.items).hasSize(3);
        assertThat(second.receipt.items.get(0).description).isEqualTo("Комплект Linen (Евро) / часть 1");
        assertThat(second.receipt.items.get(0).quantity).isEqualByComparingTo(BigDecimal.ONE);
        assertThat(second.receipt.items.get(0).amount.value).isEqualTo("33.34");
        assertThat(second.receipt.items.get(0).vatCode).isEqualTo(2);
        assertThat(second.receipt.items.get(0).paymentMode).isEqualTo("full_prepayment");
        assertThat(second.receipt.items.get(0).paymentSubject).isEqualTo("commodity");
        assertThat(second.receipt.items.get(1).quantity).isEqualByComparingTo(BigDecimal.valueOf(2));
        assertThat(second.receipt.items.get(1).amount.value).isEqualTo("33.33");
        assertThat(second.receipt.items.get(2).description).isEqualTo("Доставка");
        assertThat(second.receipt.items.get(2).amount.value).isEqualTo("12.34");
        assertThat(second.receipt.items.get(2).vatCode).isEqualTo(2);
        assertThat(second.receipt.items.get(2).paymentSubject).isEqualTo("service");
    }

    @Test
    void inactiveTaxConfigurationIsIgnoredForReceipts() {
        adminService.saveTaxConfiguration(null, taxRequest("Активный режим", 6, 2, true));
        adminService.saveTaxConfiguration(null, taxRequest("Черновик", 2, 4, false));
        Order order = order(11234);
        orders.put(order.getId(), order);

        paymentService.createYooKassaPayment(
                order.getId(),
                "buyer@example.test",
                "https://example.test/return",
                "order-key",
                false,
                null,
                "REDIRECT"
        );

        ArgumentCaptor<YooKassaClient.CreatePaymentRequest> requestCaptor =
                ArgumentCaptor.forClass(YooKassaClient.CreatePaymentRequest.class);
        verify(yooKassaClient).createPayment(requestCaptor.capture(), eq("order-key"));

        assertThat(requestCaptor.getValue().receipt.taxSystemCode).isEqualTo(6);
        assertThat(requestCaptor.getValue().receipt.items).allSatisfy(item -> assertThat(item.vatCode).isEqualTo(2));
    }

    @Test
    void updatingExistingTaxTabFiscalFieldsPersistsAndNextReceiptUsesUpdatedCodes() {
        TaxConfigurationView created = adminService.saveTaxConfiguration(
                null,
                taxRequest("Черновик налога", 6, 1, BigDecimal.ZERO, true)
        );

        TaxConfigurationView updated = adminService.saveTaxConfiguration(
                created.id(),
                taxRequest("ОСН 20", 1, 4, BigDecimal.valueOf(20), true)
        );
        Order order = order(11234);
        orders.put(order.getId(), order);

        paymentService.createYooKassaPayment(
                order.getId(),
                "buyer@example.test",
                "https://example.test/return",
                "order-key-updated-tax",
                false,
                null,
                "REDIRECT"
        );

        TaxConfigurationView persisted = adminService.getTaxConfiguration(created.id());
        assertThat(updated.id()).isEqualTo(created.id());
        assertThat(persisted.taxSystemCode()).isEqualTo(1);
        assertThat(persisted.vatCode()).isEqualTo(4);
        assertThat(persisted.vatRatePercent()).isEqualByComparingTo("20");
        assertThat(persisted.active()).isTrue();

        ArgumentCaptor<YooKassaClient.CreatePaymentRequest> requestCaptor =
                ArgumentCaptor.forClass(YooKassaClient.CreatePaymentRequest.class);
        verify(yooKassaClient).createPayment(requestCaptor.capture(), eq("order-key-updated-tax"));

        YooKassaClient.CreatePaymentRequest request = requestCaptor.getValue();
        assertThat(request.receipt.taxSystemCode).isEqualTo(1);
        assertThat(request.receipt.items).allSatisfy(item -> assertThat(item.vatCode).isEqualTo(4));
    }

    private TaxConfigurationRequest taxRequest(String name, int taxSystemCode, int vatCode, boolean active) {
        return taxRequest(name, taxSystemCode, vatCode, BigDecimal.TEN, active);
    }

    private TaxConfigurationRequest taxRequest(String name,
                                               int taxSystemCode,
                                               int vatCode,
                                               BigDecimal vatRatePercent,
                                               boolean active) {
        return new TaxConfigurationRequest(
                name,
                "ACTIVE",
                taxSystemCode,
                vatCode,
                vatRatePercent,
                active
        );
    }

    private Order order(long totalAmount) {
        UUID orderId = UUID.randomUUID();
        Order order = new Order(UUID.randomUUID(), "PENDING", Money.of(totalAmount, "RUB"));
        order.setId(orderId);
        order.setPublicToken("public-token-" + orderId);
        order.setReceiptEmail("buyer@example.test");
        order.setContactPhone("+7 (999) 000-00-00");
        order.setDeliveryAmount(Money.of(1234, "RUB"));

        OrderItem item = new OrderItem(UUID.randomUUID(), 3, Money.of(4000, "RUB"));
        item.setProductName("Комплект Linen");
        item.setVariantName("Евро");
        item.setPayableAmount(Money.of(10000, "RUB"));
        order.addItem(item);
        return order;
    }

    private void stubTaxRepository() {
        when(taxConfigurationRepository.findAll()).thenAnswer(invocation -> new ArrayList<>(taxRows.values()));
        when(taxConfigurationRepository.findAll(any(Sort.class))).thenAnswer(invocation ->
                taxRows.values().stream()
                        .sorted(Comparator.comparing(TaxConfiguration::isActive).reversed()
                                .thenComparing(TaxConfiguration::getName))
                        .toList()
        );
        when(taxConfigurationRepository.findById(any(UUID.class))).thenAnswer(invocation ->
                Optional.ofNullable(taxRows.get(invocation.getArgument(0)))
        );
        when(taxConfigurationRepository.findFirstByActiveTrueAndStatusIgnoreCase(anyString())).thenAnswer(invocation -> {
            String status = invocation.getArgument(0);
            return taxRows.values().stream()
                    .filter(TaxConfiguration::isActive)
                    .filter(config -> config.getStatus() != null && config.getStatus().equalsIgnoreCase(status))
                    .findFirst();
        });
        when(taxConfigurationRepository.save(any(TaxConfiguration.class))).thenAnswer(invocation -> {
            TaxConfiguration config = invocation.getArgument(0);
            if (config.getId() == null) {
                config.setId(UUID.randomUUID());
                config.setCreatedAt(OffsetDateTime.now());
            }
            config.setUpdatedAt(OffsetDateTime.now());
            taxRows.put(config.getId(), config);
            return config;
        });
    }

    @SuppressWarnings("unchecked")
    private <T> ObjectProvider<T> provider(T value) {
        ObjectProvider<T> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(value);
        return provider;
    }
}
