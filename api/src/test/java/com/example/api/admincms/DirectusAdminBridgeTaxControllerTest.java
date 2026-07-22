package com.example.api.admincms;

import com.example.admin.service.AdminActivityService;
import com.example.api.admincms.DirectusAdminModels.TaxConfigurationRequest;
import com.example.api.admincms.DirectusAdminModels.TaxConfigurationView;
import com.example.api.catalog.DirectusBridgeSecurity;
import com.example.api.config.GlobalExceptionHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class DirectusAdminBridgeTaxControllerTest {

    @Mock
    private DirectusAdminService adminService;
    @Mock
    private DirectusBridgeSecurity bridgeSecurity;
    @Mock
    private DirectusAdminRoleGuard roleGuard;
    @Mock
    private AdminActivityService adminActivityService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        DirectusAdminBridgeController controller = new DirectusAdminBridgeController(
                adminService,
                bridgeSecurity,
                roleGuard,
                adminActivityService,
                new ObjectMapper()
        );
        doNothing().when(bridgeSecurity).authorize(any());
        when(bridgeSecurity.principal(any())).thenReturn(new DirectusBridgeSecurity.DirectusBridgePrincipal(
                "directus-user-1",
                "admin@example.test",
                "external-admin",
                "admin",
                "admin"
        ));

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void postTaxSettingsAcceptsDirectusTaxTabPayload() throws Exception {
        UUID savedId = UUID.randomUUID();
        when(adminService.saveTaxConfiguration(isNull(), any(TaxConfigurationRequest.class))).thenReturn(
                new TaxConfigurationView(savedId, "УСН доходы", "ACTIVE", 6, 2, BigDecimal.TEN, true, null)
        );

        mockMvc.perform(post("/internal/directus/admin/tax-settings")
                        .contentType("application/json")
                        .content("""
                                {
                                  "name": "УСН доходы",
                                  "status": "ACTIVE",
                                  "taxSystemCode": 6,
                                  "vatCode": 2,
                                  "vatRatePercent": 10,
                                  "active": true
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(savedId.toString()))
                .andExpect(jsonPath("$.taxSystemCode").value(6))
                .andExpect(jsonPath("$.vatCode").value(2))
                .andExpect(jsonPath("$.vatRatePercent").value(10))
                .andExpect(jsonPath("$.active").value(true));

        ArgumentCaptor<TaxConfigurationRequest> requestCaptor = ArgumentCaptor.forClass(TaxConfigurationRequest.class);
        verify(roleGuard).requireTax(any());
        verify(adminService).saveTaxConfiguration(isNull(), requestCaptor.capture());
        assertThat(requestCaptor.getValue().name()).isEqualTo("УСН доходы");
        assertThat(requestCaptor.getValue().status()).isEqualTo("ACTIVE");
        assertThat(requestCaptor.getValue().taxSystemCode()).isEqualTo(6);
        assertThat(requestCaptor.getValue().vatCode()).isEqualTo(2);
        assertThat(requestCaptor.getValue().vatRatePercent()).isEqualByComparingTo("10");
        assertThat(requestCaptor.getValue().active()).isTrue();
        verify(adminActivityService).record(eq("admin@example.test"), eq("admin.tax.create"), any());
    }

    @Test
    void putTaxSettingsAcceptsDirectusTaxTabPayload() throws Exception {
        UUID id = UUID.randomUUID();
        when(adminService.saveTaxConfiguration(eq(id), any(TaxConfigurationRequest.class))).thenReturn(
                new TaxConfigurationView(id, "ОСН 20", "ACTIVE", 1, 4, BigDecimal.valueOf(20), true, null)
        );

        mockMvc.perform(put("/internal/directus/admin/tax-settings/{id}", id)
                        .contentType("application/json")
                        .content("""
                                {
                                  "name": "ОСН 20",
                                  "status": "ACTIVE",
                                  "taxSystemCode": 1,
                                  "vatCode": 4,
                                  "vatRatePercent": 20,
                                  "active": true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.taxSystemCode").value(1))
                .andExpect(jsonPath("$.vatCode").value(4))
                .andExpect(jsonPath("$.vatRatePercent").value(20));

        ArgumentCaptor<TaxConfigurationRequest> requestCaptor = ArgumentCaptor.forClass(TaxConfigurationRequest.class);
        verify(adminService).saveTaxConfiguration(eq(id), requestCaptor.capture());
        assertThat(requestCaptor.getValue().name()).isEqualTo("ОСН 20");
        assertThat(requestCaptor.getValue().taxSystemCode()).isEqualTo(1);
        assertThat(requestCaptor.getValue().vatCode()).isEqualTo(4);
        assertThat(requestCaptor.getValue().vatRatePercent()).isEqualByComparingTo("20");
        assertThat(requestCaptor.getValue().active()).isTrue();
        verify(adminActivityService).record(eq("admin@example.test"), eq("admin.tax.update"), any());
    }

    @Test
    void taxSettingsRequireAdminBridgeRole() throws Exception {
        doThrow(new AccessDeniedException("Directus role is not allowed to access tax settings"))
                .when(roleGuard).requireTax(any());

        mockMvc.perform(post("/internal/directus/admin/tax-settings")
                        .contentType("application/json")
                        .content("""
                                {
                                  "name": "УСН доходы",
                                  "status": "ACTIVE",
                                  "taxSystemCode": 6,
                                  "vatCode": 2,
                                  "vatRatePercent": 10,
                                  "active": true
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        verify(adminService, never()).saveTaxConfiguration(any(), any());
    }
}
