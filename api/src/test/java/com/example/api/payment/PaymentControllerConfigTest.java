package com.example.api.payment;

import com.example.api.admincms.DirectusAdminModels.TaxConfigurationView;
import com.example.api.admincms.DirectusAdminService;
import com.example.api.notification.NotificationOrchestrator;
import com.example.order.service.OrderService;
import com.example.payment.service.PaymentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PaymentControllerConfigTest {

    @Test
    @SuppressWarnings("unchecked")
    void publicConfigAdvertisesYooKassaFullPrepaymentAndActiveFiscalConfig() {
        DirectusAdminService directusAdminService = mock(DirectusAdminService.class);
        ObjectProvider<DirectusAdminService> directusProvider = mock(ObjectProvider.class);
        when(directusProvider.getIfAvailable()).thenReturn(directusAdminService);
        UUID configId = UUID.randomUUID();
        when(directusAdminService.activeTaxConfigurationView()).thenReturn(Optional.of(
                new TaxConfigurationView(
                        configId,
                        "УСН доходы",
                        "ACTIVE",
                        6,
                        2,
                        BigDecimal.valueOf(10),
                        true,
                        null
                )
        ));
        PaymentController controller = new PaymentController(
                mock(PaymentService.class),
                mock(OrderService.class),
                mock(NotificationOrchestrator.class),
                mock(YooKassaWebhookVerifier.class),
                directusProvider
        );
        ReflectionTestUtils.setField(controller, "yooKassaEnabled", true);

        PaymentController.PublicPaymentConfig config = controller.getPublicConfig().getBody();

        assertThat(config).isNotNull();
        assertThat(config.enabled()).isTrue();
        assertThat(config.providerCode()).isEqualTo("YOOKASSA");
        assertThat(config.methods()).containsExactly("CARD", "SBP");
        assertThat(config.fullPrepayment()).isTrue();
        assertThat(config.splitPaymentsEnabled()).isFalse();
        assertThat(config.cashOnDeliveryEnabled()).isFalse();
        assertThat(config.fiscalConfig().id()).isEqualTo(configId);
        assertThat(config.fiscalConfig().taxSystemCode()).isEqualTo(6);
        assertThat(config.fiscalConfig().vatCode()).isEqualTo(2);
    }
}
