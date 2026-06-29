package com.example.api.catalog;

import com.example.admin.service.AdminActivityService;
import com.example.api.config.GlobalExceptionHandler;
import com.example.catalog.domain.ProductVariant;
import com.example.catalog.service.CatalogService;
import com.example.catalog.service.InventoryService;
import com.example.common.domain.Money;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class DirectusBridgeControllerTest {

    private static final String BRIDGE_TOKEN = "bridge-token";

    @Mock
    private CatalogService catalogService;
    @Mock
    private InventoryService inventoryService;
    @Mock
    private ProductImageStorageService productImageStorageService;
    @Mock
    private CategoryImageStorageService categoryImageStorageService;
    @Mock
    private CatalogueResponseFactory responseFactory;
    @Mock
    private CataloguePresentationService presentationService;
    @Mock
    private AdminActivityService adminActivityService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        DirectusBridgeSecurity bridgeSecurity = new DirectusBridgeSecurity();
        ReflectionTestUtils.setField(bridgeSecurity, "bridgeToken", BRIDGE_TOKEN);

        DirectusStorefrontOpsRolePolicy rolePolicy = new DirectusStorefrontOpsRolePolicy();
        ReflectionTestUtils.setField(rolePolicy, "adminRoles", "admin,admin-role");
        ReflectionTestUtils.setField(rolePolicy, "managerRoles", "manager-role");
        ReflectionTestUtils.setField(rolePolicy, "pickerRoles", "picker-role");
        ReflectionTestUtils.setField(rolePolicy, "contentManagerRoles", "content-role");
        ReflectionTestUtils.setField(rolePolicy, "catalogueRoles", "catalogue-role");
        ReflectionTestUtils.setField(rolePolicy, "inventoryRoles", "inventory-role");

        DirectusBridgeController controller = new DirectusBridgeController(
                catalogService,
                inventoryService,
                productImageStorageService,
                categoryImageStorageService,
                responseFactory,
                presentationService,
                bridgeSecurity,
                rolePolicy,
                adminActivityService,
                new ObjectMapper()
        );

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void listProducts_requiresBridgeToken() throws Exception {
        mockMvc.perform(get("/internal/directus/catalogue/products"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("DIRECTUS_BRIDGE_UNAUTHORIZED"));
    }

    @Test
    void listProducts_rejectsNonCatalogueRoleEvenWithValidBridgeToken() throws Exception {
        mockMvc.perform(withBridgeRole(get("/internal/directus/catalogue/products"), "manager-role"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void listProducts_allowsCatalogueRole() throws Exception {
        when(catalogService.getAllProducts()).thenReturn(List.of());
        when(presentationService.buildPublishedProductPresentationResults(anyList())).thenReturn(Map.of());

        mockMvc.perform(withBridgeRole(get("/internal/directus/catalogue/products"), "catalogue-role"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());

        verify(catalogService).getAllProducts();
    }

    @Test
    void listProducts_rejectsInventoryOnlyRole() throws Exception {
        mockMvc.perform(withBridgeRole(get("/internal/directus/catalogue/products"), "inventory-role"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void adjustInventory_rejectsCatalogueOnlyRole() throws Exception {
        UUID variantId = UUID.fromString("11111111-1111-4111-8111-111111111111");

        mockMvc.perform(withBridgeRole(post("/internal/directus/catalogue/inventory/adjust")
                        .param("idempotencyKey", "adj-1")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"variantId":"%s","delta":3,"reason":"manual"}
                                """.formatted(variantId)), "catalogue-role"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void adjustInventory_allowsInventoryRole() throws Exception {
        UUID variantId = UUID.fromString("11111111-1111-4111-8111-111111111111");
        ProductVariant variant = new ProductVariant("SKU-1", "Linen / white", Money.of(1000, "RUB"), 7);
        variant.setId(variantId);
        when(inventoryService.adjustStock(eq(variantId), eq(3), eq("adj-1"), eq("manual")))
                .thenReturn(new InventoryService.AdjustmentResult(variant, true, null));

        mockMvc.perform(withBridgeRole(post("/internal/directus/catalogue/inventory/adjust")
                        .param("idempotencyKey", "adj-1")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"variantId":"%s","delta":3,"reason":"manual"}
                                """.formatted(variantId)), "inventory-role"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.variantId").value(variantId.toString()))
                .andExpect(jsonPath("$.stock").value(7))
                .andExpect(jsonPath("$.applied").value(true));
    }

    @Test
    void updateVariant_rejectsSkuChangeForInventoryRole() throws Exception {
        UUID productId = UUID.fromString("22222222-2222-4222-8222-222222222222");
        UUID variantId = UUID.fromString("33333333-3333-4333-8333-333333333333");
        ProductVariant current = new ProductVariant("SKU-1", "Linen / white", Money.of(1000, "RUB"), 7);
        current.setId(variantId);
        when(catalogService.getVariant(eq(productId), eq(variantId))).thenReturn(current);

        mockMvc.perform(withBridgeRole(put("/internal/directus/catalogue/products/{productId}/variants/{variantId}", productId, variantId)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"sku":"SKU-2","name":"Linen / white","amount":1000,"currency":"RUB","stock":7}
                                """), "inventory-role"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void updateVariant_allowsUnchangedSkuForInventoryRole() throws Exception {
        UUID productId = UUID.fromString("22222222-2222-4222-8222-222222222222");
        UUID variantId = UUID.fromString("33333333-3333-4333-8333-333333333333");
        ProductVariant current = new ProductVariant("SKU-1", "Linen / white updated", Money.of(1200, "RUB"), 9);
        current.setId(variantId);
        when(catalogService.getVariant(eq(productId), eq(variantId))).thenReturn(current);
        when(catalogService.updateVariant(
                eq(productId),
                eq(variantId),
                isNull(),
                eq("Linen / white updated"),
                any(Money.class),
                eq(9),
                isNull(),
                isNull(),
                isNull(),
                isNull(),
                isNull(),
                isNull(),
                isNull(),
                isNull(),
                isNull(),
                isNull()
        )).thenReturn(current);

        mockMvc.perform(withBridgeRole(put("/internal/directus/catalogue/products/{productId}/variants/{variantId}", productId, variantId)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"sku":"SKU-1","name":"Linen / white updated","amount":1200,"currency":"RUB","stock":9}
                                """), "inventory-role"))
                .andExpect(status().isOk());

        verify(catalogService).updateVariant(
                eq(productId),
                eq(variantId),
                isNull(),
                eq("Linen / white updated"),
                any(Money.class),
                eq(9),
                isNull(),
                isNull(),
                isNull(),
                isNull(),
                isNull(),
                isNull(),
                isNull(),
                isNull(),
                isNull(),
                isNull()
        );
    }

    @Test
    void updateVariant_allowsSkuChangeForAdminRole() throws Exception {
        UUID productId = UUID.fromString("22222222-2222-4222-8222-222222222222");
        UUID variantId = UUID.fromString("33333333-3333-4333-8333-333333333333");
        ProductVariant updated = new ProductVariant("SKU-2", "Linen / white updated", Money.of(1200, "RUB"), 9);
        updated.setId(variantId);
        when(catalogService.updateVariant(
                eq(productId),
                eq(variantId),
                eq("SKU-2"),
                eq("Linen / white updated"),
                any(Money.class),
                eq(9),
                isNull(),
                isNull(),
                isNull(),
                isNull(),
                isNull(),
                isNull(),
                isNull(),
                isNull(),
                isNull(),
                isNull()
        )).thenReturn(updated);

        mockMvc.perform(withBridgeRole(put("/internal/directus/catalogue/products/{productId}/variants/{variantId}", productId, variantId)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"sku":"SKU-2","name":"Linen / white updated","amount":1200,"currency":"RUB","stock":9}
                                """), "admin-role"))
                .andExpect(status().isOk());

        verify(catalogService).updateVariant(
                eq(productId),
                eq(variantId),
                eq("SKU-2"),
                eq("Linen / white updated"),
                any(Money.class),
                eq(9),
                isNull(),
                isNull(),
                isNull(),
                isNull(),
                isNull(),
                isNull(),
                isNull(),
                isNull(),
                isNull(),
                isNull()
        );
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder withBridgeRole(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,
            String role
    ) {
        return request
                .header(DirectusBridgeSecurity.BRIDGE_TOKEN_HEADER, BRIDGE_TOKEN)
                .header(DirectusBridgeSecurity.USER_ID_HEADER, "directus-user-1")
                .header(DirectusBridgeSecurity.USER_EMAIL_HEADER, "editor@example.test")
                .header(DirectusBridgeSecurity.USER_ROLE_HEADER, role)
                .header(DirectusBridgeSecurity.USER_ROLES_HEADER, role);
    }
}
