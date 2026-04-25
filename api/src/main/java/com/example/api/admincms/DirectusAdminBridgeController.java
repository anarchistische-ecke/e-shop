package com.example.api.admincms;

import com.example.admin.service.AdminActivityService;
import com.example.api.admincms.DirectusAdminModels.ImportCommitResponse;
import com.example.api.admincms.DirectusAdminModels.ImportDryRunResponse;
import com.example.api.admincms.DirectusAdminModels.ImportJobView;
import com.example.api.admincms.DirectusAdminModels.ImportMapping;
import com.example.api.admincms.DirectusAdminModels.LowStockAlertResponse;
import com.example.api.admincms.DirectusAdminModels.ManagerAnalyticsResponse;
import com.example.api.admincms.DirectusAdminModels.OrderDetail;
import com.example.api.admincms.DirectusAdminModels.OrderRefundRequest;
import com.example.api.admincms.DirectusAdminModels.OrderSearchResponse;
import com.example.api.admincms.DirectusAdminModels.OrderStatusRequest;
import com.example.api.admincms.DirectusAdminModels.PaymentLinkAnalyticsResponse;
import com.example.api.admincms.DirectusAdminModels.PromoCodeRequest;
import com.example.api.admincms.DirectusAdminModels.PromoCodeView;
import com.example.api.admincms.DirectusAdminModels.PromotionRequest;
import com.example.api.admincms.DirectusAdminModels.PromotionView;
import com.example.api.admincms.DirectusAdminModels.RmaDecisionRequest;
import com.example.api.admincms.DirectusAdminModels.RmaRequestCreateRequest;
import com.example.api.admincms.DirectusAdminModels.RmaRequestListResponse;
import com.example.api.admincms.DirectusAdminModels.RmaRequestView;
import com.example.api.admincms.DirectusAdminModels.StockAlertSettingsRequest;
import com.example.api.admincms.DirectusAdminModels.TaxConfigurationRequest;
import com.example.api.admincms.DirectusAdminModels.TaxConfigurationView;
import com.example.api.catalog.DirectusBridgeSecurity;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/internal/directus/admin")
public class DirectusAdminBridgeController {

    private final DirectusAdminService adminService;
    private final DirectusBridgeSecurity bridgeSecurity;
    private final DirectusAdminRoleGuard roleGuard;
    private final AdminActivityService adminActivityService;
    private final ObjectMapper objectMapper;

    public DirectusAdminBridgeController(
            DirectusAdminService adminService,
            DirectusBridgeSecurity bridgeSecurity,
            DirectusAdminRoleGuard roleGuard,
            AdminActivityService adminActivityService,
            ObjectMapper objectMapper
    ) {
        this.adminService = adminService;
        this.bridgeSecurity = bridgeSecurity;
        this.roleGuard = roleGuard;
        this.adminActivityService = adminActivityService;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/orders")
    public ResponseEntity<OrderSearchResponse> listOrders(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String manager,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            @RequestParam(required = false) String q,
            HttpServletRequest request
    ) {
        var principal = authorize(request);
        roleGuard.requireOrders(principal);
        return ResponseEntity.ok(adminService.searchOrders(status, manager, from, to, q, principal));
    }

    @GetMapping("/orders/{id}")
    public ResponseEntity<OrderDetail> getOrder(@PathVariable UUID id, HttpServletRequest request) {
        var principal = authorize(request);
        roleGuard.requireOrders(principal);
        return ResponseEntity.ok(adminService.getOrder(id, principal));
    }

    @PostMapping("/orders/{id}/status")
    public ResponseEntity<OrderDetail> updateOrderStatus(
            @PathVariable UUID id,
            @RequestBody OrderStatusRequest requestBody,
            HttpServletRequest request
    ) {
        var principal = authorize(request);
        roleGuard.requireOrders(principal);
        OrderDetail response = adminService.updateOrderStatus(id, requestBody.status(), requestBody.note(), principal);
        audit(principal, "admin.order.status", Map.of("orderId", id, "status", requestBody.status()));
        return ResponseEntity.ok(response);
    }

    @PostMapping("/orders/{id}/claim")
    public ResponseEntity<OrderDetail> claimOrder(@PathVariable UUID id, HttpServletRequest request) {
        var principal = authorize(request);
        roleGuard.requireOrders(principal);
        OrderDetail response = adminService.claimOrder(id, principal);
        audit(principal, "admin.order.claim", Map.of("orderId", id));
        return ResponseEntity.ok(response);
    }

    @PostMapping("/orders/{id}/unclaim")
    public ResponseEntity<OrderDetail> clearOrderClaim(@PathVariable UUID id, HttpServletRequest request) {
        var principal = authorize(request);
        roleGuard.requireOrders(principal);
        OrderDetail response = adminService.clearOrderClaim(id, principal);
        audit(principal, "admin.order.unclaim", Map.of("orderId", id));
        return ResponseEntity.ok(response);
    }

    @PostMapping("/orders/{id}/refunds")
    public ResponseEntity<OrderDetail> refundOrder(
            @PathVariable UUID id,
            @RequestBody(required = false) OrderRefundRequest requestBody,
            HttpServletRequest request
    ) {
        var principal = authorize(request);
        roleGuard.requireAdmin(principal, "payment refunds");
        OrderDetail response = adminService.refundOrder(id, requestBody, principal);
        audit(principal, "admin.order.refund", Map.of("orderId", id));
        return ResponseEntity.ok(response);
    }

    @GetMapping("/rma-requests")
    public ResponseEntity<RmaRequestListResponse> listRmaRequests(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) UUID orderId,
            HttpServletRequest request
    ) {
        var principal = authorize(request);
        roleGuard.requireRma(principal);
        return ResponseEntity.ok(adminService.listRmaRequests(status, orderId, principal));
    }

    @PostMapping("/rma-requests")
    public ResponseEntity<RmaRequestView> createRmaRequest(
            @RequestBody RmaRequestCreateRequest requestBody,
            HttpServletRequest request
    ) {
        var principal = authorize(request);
        roleGuard.requireRma(principal);
        RmaRequestView response = adminService.createRmaRequest(requestBody, principal);
        audit(principal, "admin.rma.create", Map.of("rmaId", response.id(), "orderId", response.orderId()));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/rma-requests/{id}/decision")
    public ResponseEntity<RmaRequestView> decideRmaRequest(
            @PathVariable UUID id,
            @RequestBody RmaDecisionRequest requestBody,
            HttpServletRequest request
    ) {
        var principal = authorize(request);
        roleGuard.requireRma(principal);
        RmaRequestView response = adminService.decideRmaRequest(id, requestBody, principal);
        audit(principal, "admin.rma.decision", Map.of("rmaId", id, "status", response.status()));
        return ResponseEntity.ok(response);
    }

    @PostMapping(value = "/imports/catalogue/dry-run", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ImportDryRunResponse> dryRunCatalogueImport(
            @RequestPart("file") MultipartFile file,
            @RequestParam(value = "mapping", required = false) String mappingJson,
            HttpServletRequest request
    ) {
        var principal = authorize(request);
        roleGuard.requireContent(principal);
        ImportMapping mapping = parseMapping(mappingJson);
        ImportDryRunResponse response = adminService.dryRunImport(file, mapping, principal);
        audit(principal, "admin.import.dry-run", Map.of(
                "jobId", response.job().id(),
                "fileName", response.job().fileName(),
                "totalRows", response.job().totalRows(),
                "invalidRows", response.job().invalidRows()
        ));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/imports/catalogue/commit")
    public ResponseEntity<ImportCommitResponse> commitCatalogueImport(
            @RequestParam(required = false) UUID jobId,
            @RequestBody(required = false) Map<String, Object> requestBody,
            HttpServletRequest request
    ) {
        var principal = authorize(request);
        roleGuard.requireContent(principal);
        UUID resolvedJobId = jobId != null ? jobId : readUuid(requestBody, "jobId");
        if (resolvedJobId == null) {
            throw new IllegalArgumentException("jobId is required");
        }
        ImportCommitResponse response = adminService.commitImport(resolvedJobId);
        audit(principal, "admin.import.commit", Map.of(
                "jobId", response.job().id(),
                "appliedRows", response.appliedRows()
        ));
        return ResponseEntity.ok(response);
    }

    @GetMapping("/imports")
    public ResponseEntity<List<ImportJobView>> listImports(HttpServletRequest request) {
        var principal = authorize(request);
        roleGuard.requireContent(principal);
        return ResponseEntity.ok(adminService.listImports());
    }

    @GetMapping("/promotions")
    public ResponseEntity<List<PromotionView>> listPromotions(HttpServletRequest request) {
        var principal = authorize(request);
        roleGuard.requireContent(principal);
        return ResponseEntity.ok(adminService.listPromotions());
    }

    @GetMapping("/promotions/active")
    public ResponseEntity<List<PromotionView>> activePromotions(HttpServletRequest request) {
        var principal = authorize(request);
        roleGuard.requirePromotionsRead(principal);
        return ResponseEntity.ok(adminService.activePromotions());
    }

    @GetMapping("/promotions/{id}")
    public ResponseEntity<PromotionView> getPromotion(@PathVariable UUID id, HttpServletRequest request) {
        var principal = authorize(request);
        roleGuard.requireContent(principal);
        return ResponseEntity.ok(adminService.getPromotion(id));
    }

    @PostMapping("/promotions")
    public ResponseEntity<PromotionView> createPromotion(@RequestBody PromotionRequest requestBody, HttpServletRequest request) {
        var principal = authorize(request);
        roleGuard.requireContent(principal);
        PromotionView response = adminService.savePromotion(null, requestBody);
        audit(principal, "admin.promotion.create", Map.of("promotionId", response.id(), "type", response.type()));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/promotions/{id}")
    public ResponseEntity<PromotionView> updatePromotion(
            @PathVariable UUID id,
            @RequestBody PromotionRequest requestBody,
            HttpServletRequest request
    ) {
        var principal = authorize(request);
        roleGuard.requireContent(principal);
        PromotionView response = adminService.savePromotion(id, requestBody);
        audit(principal, "admin.promotion.update", Map.of("promotionId", id, "type", response.type()));
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/promotions/{id}")
    public ResponseEntity<Void> deletePromotion(@PathVariable UUID id, HttpServletRequest request) {
        var principal = authorize(request);
        roleGuard.requireContent(principal);
        adminService.deletePromotion(id);
        audit(principal, "admin.promotion.delete", Map.of("promotionId", id));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/promo-codes")
    public ResponseEntity<List<PromoCodeView>> listPromoCodes(HttpServletRequest request) {
        var principal = authorize(request);
        roleGuard.requireContent(principal);
        return ResponseEntity.ok(adminService.listPromoCodes());
    }

    @GetMapping("/promo-codes/{id}")
    public ResponseEntity<PromoCodeView> getPromoCode(@PathVariable UUID id, HttpServletRequest request) {
        var principal = authorize(request);
        roleGuard.requireContent(principal);
        return ResponseEntity.ok(adminService.getPromoCode(id));
    }

    @PostMapping("/promo-codes")
    public ResponseEntity<PromoCodeView> createPromoCode(@RequestBody PromoCodeRequest requestBody, HttpServletRequest request) {
        var principal = authorize(request);
        roleGuard.requireContent(principal);
        PromoCodeView response = adminService.savePromoCode(null, requestBody);
        audit(principal, "admin.promo-code.create", Map.of("promoCodeId", response.id(), "code", response.code()));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/promo-codes/{id}")
    public ResponseEntity<PromoCodeView> updatePromoCode(
            @PathVariable UUID id,
            @RequestBody PromoCodeRequest requestBody,
            HttpServletRequest request
    ) {
        var principal = authorize(request);
        roleGuard.requireContent(principal);
        PromoCodeView response = adminService.savePromoCode(id, requestBody);
        audit(principal, "admin.promo-code.update", Map.of("promoCodeId", id, "code", response.code()));
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/promo-codes/{id}")
    public ResponseEntity<Void> deletePromoCode(@PathVariable UUID id, HttpServletRequest request) {
        var principal = authorize(request);
        roleGuard.requireContent(principal);
        adminService.deletePromoCode(id);
        audit(principal, "admin.promo-code.delete", Map.of("promoCodeId", id));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/tax-settings")
    public ResponseEntity<List<TaxConfigurationView>> listTaxSettings(HttpServletRequest request) {
        var principal = authorize(request);
        roleGuard.requireTax(principal);
        return ResponseEntity.ok(adminService.listTaxConfigurations());
    }

    @GetMapping("/tax-settings/active")
    public ResponseEntity<TaxConfigurationView> activeTaxSetting(HttpServletRequest request) {
        var principal = authorize(request);
        roleGuard.requireTax(principal);
        return ResponseEntity.of(adminService.activeTaxConfigurationView());
    }

    @GetMapping("/tax-settings/{id}")
    public ResponseEntity<TaxConfigurationView> getTaxSetting(@PathVariable UUID id, HttpServletRequest request) {
        var principal = authorize(request);
        roleGuard.requireTax(principal);
        return ResponseEntity.ok(adminService.getTaxConfiguration(id));
    }

    @PostMapping("/tax-settings")
    public ResponseEntity<TaxConfigurationView> createTaxSetting(
            @RequestBody TaxConfigurationRequest requestBody,
            HttpServletRequest request
    ) {
        var principal = authorize(request);
        roleGuard.requireTax(principal);
        TaxConfigurationView response = adminService.saveTaxConfiguration(null, requestBody);
        audit(principal, "admin.tax.create", Map.of("taxConfigurationId", response.id(), "active", response.active()));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/tax-settings/{id}")
    public ResponseEntity<TaxConfigurationView> updateTaxSetting(
            @PathVariable UUID id,
            @RequestBody TaxConfigurationRequest requestBody,
            HttpServletRequest request
    ) {
        var principal = authorize(request);
        roleGuard.requireTax(principal);
        TaxConfigurationView response = adminService.saveTaxConfiguration(id, requestBody);
        audit(principal, "admin.tax.update", Map.of("taxConfigurationId", id, "active", response.active()));
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/tax-settings/{id}")
    public ResponseEntity<Void> deleteTaxSetting(@PathVariable UUID id, HttpServletRequest request) {
        var principal = authorize(request);
        roleGuard.requireTax(principal);
        adminService.deleteTaxConfiguration(id);
        audit(principal, "admin.tax.delete", Map.of("taxConfigurationId", id));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/analytics/managers")
    public ResponseEntity<ManagerAnalyticsResponse> managerAnalytics(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            @RequestParam(required = false) String manager,
            HttpServletRequest request
    ) {
        var principal = authorize(request);
        roleGuard.requireAnalytics(principal);
        return ResponseEntity.ok(adminService.managerAnalytics(from, to, analyticsManagerFilter(principal, manager)));
    }

    @GetMapping("/analytics/payment-links")
    public ResponseEntity<PaymentLinkAnalyticsResponse> paymentLinkAnalytics(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            @RequestParam(required = false) String manager,
            HttpServletRequest request
    ) {
        var principal = authorize(request);
        roleGuard.requireAnalytics(principal);
        return ResponseEntity.ok(adminService.paymentLinkAnalytics(from, to, analyticsManagerFilter(principal, manager)));
    }

    @GetMapping("/alerts/low-stock")
    public ResponseEntity<LowStockAlertResponse> lowStockAlerts(HttpServletRequest request) {
        var principal = authorize(request);
        roleGuard.requireContent(principal);
        return ResponseEntity.ok(adminService.lowStockAlerts());
    }

    @PutMapping("/alerts/low-stock/settings")
    public ResponseEntity<LowStockAlertResponse> updateLowStockSettings(
            @RequestBody StockAlertSettingsRequest requestBody,
            HttpServletRequest request
    ) {
        var principal = authorize(request);
        roleGuard.requireContent(principal);
        LowStockAlertResponse response = adminService.updateStockAlertSettings(requestBody);
        audit(principal, "admin.alerts.low-stock.settings", Map.of("threshold", response.threshold()));
        return ResponseEntity.ok(response);
    }

    private DirectusBridgeSecurity.DirectusBridgePrincipal authorize(HttpServletRequest request) {
        bridgeSecurity.authorize(request);
        return bridgeSecurity.principal(request);
    }

    private ImportMapping parseMapping(String mappingJson) {
        if (!StringUtils.hasText(mappingJson)) {
            return null;
        }
        try {
            return objectMapper.readValue(mappingJson, ImportMapping.class);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Invalid import mapping JSON", ex);
        }
    }

    private UUID readUuid(Map<String, Object> body, String key) {
        Object value = body != null ? body.get(key) : null;
        if (value == null || !StringUtils.hasText(String.valueOf(value))) {
            return null;
        }
        return UUID.fromString(String.valueOf(value));
    }

    private String analyticsManagerFilter(DirectusBridgeSecurity.DirectusBridgePrincipal principal, String requestedManager) {
        if (!roleGuard.isManager(principal) || roleGuard.isAdmin(principal)) {
            return requestedManager;
        }
        if (StringUtils.hasText(principal.email())) {
            return principal.email();
        }
        if (StringUtils.hasText(principal.externalId())) {
            return principal.externalId();
        }
        return principal.userId();
    }

    private void audit(DirectusBridgeSecurity.DirectusBridgePrincipal principal, String action, Map<String, Object> details) {
        try {
            adminActivityService.record(principal.actor(), action, objectMapper.writeValueAsString(enrichDetails(principal, details)));
        } catch (Exception error) {
            adminActivityService.record(principal.actor(), action, String.valueOf(enrichDetails(principal, details)));
        }
    }

    private Map<String, Object> enrichDetails(DirectusBridgeSecurity.DirectusBridgePrincipal principal, Map<String, Object> details) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("directusUserId", principal.userId());
        payload.put("directusEmail", principal.email());
        payload.put("directusPrimaryRole", principal.primaryRole());
        payload.put("directusRoles", principal.roles());
        if (details != null) {
            payload.putAll(details);
        }
        return payload;
    }
}
