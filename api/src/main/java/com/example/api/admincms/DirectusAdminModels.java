package com.example.api.admincms;

import com.example.common.domain.Money;
import com.example.order.domain.Order;
import com.example.order.domain.RmaRequest;
import com.example.shipment.domain.Shipment;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class DirectusAdminModels {

    private DirectusAdminModels() {
    }

    public record OrderSearchResponse(List<OrderSummary> items) {
    }

    public record OrderSummary(
            UUID id,
            String status,
            Money totalAmount,
            String receiptEmail,
            String managerSubject,
            String managerEmail,
            String managerDirectusUserId,
            OffsetDateTime managerClaimedAt,
            OffsetDateTime orderDate,
            int itemCount,
            String publicToken
    ) {
        public static OrderSummary from(Order order) {
            return new OrderSummary(
                    order.getId(),
                    order.getStatus(),
                    order.getTotalAmount(),
                    order.getReceiptEmail(),
                    order.getManagerSubject(),
                    order.getManagerEmail(),
                    order.getManagerDirectusUserId(),
                    order.getManagerClaimedAt(),
                    order.getOrderDate(),
                    order.getItems() != null ? order.getItems().size() : 0,
                    order.getPublicToken()
            );
        }
    }

    public record OrderDetail(Order order, List<OrderStatusEvent> history, ShipmentView shipment, List<RmaRequestView> rmaRequests) {
    }

    public record OrderStatusEvent(
            UUID id,
            String previousStatus,
            String nextStatus,
            String actor,
            String actorRole,
            String note,
            OffsetDateTime createdAt
    ) {
        public static OrderStatusEvent from(OrderStatusHistory history) {
            return new OrderStatusEvent(
                    history.getId(),
                    history.getPreviousStatus(),
                    history.getNextStatus(),
                    history.getActor(),
                    history.getActorRole(),
                    history.getNote(),
                    history.getCreatedAt()
            );
        }
    }

    public record OrderStatusRequest(String status, String note) {
    }

    public record OrderRefundRequest(List<OrderRefundLineRequest> items) {
    }

    public record OrderRefundLineRequest(UUID orderItemId, Integer quantity, Long amount) {
    }

    public record ShipmentView(
            UUID id,
            UUID orderId,
            String carrier,
            String trackingNumber,
            OffsetDateTime shippedAt,
            OffsetDateTime deliveredAt
    ) {
        public static ShipmentView from(Shipment shipment) {
            if (shipment == null) {
                return null;
            }
            return new ShipmentView(
                    shipment.getId(),
                    shipment.getOrderId(),
                    shipment.getCarrier(),
                    shipment.getTrackingNumber(),
                    shipment.getShippedAt(),
                    shipment.getDeliveredAt()
            );
        }
    }

    public record RmaRequestCreateRequest(UUID orderId, String reason, String desiredResolution) {
    }

    public record RmaDecisionRequest(String status, String comment) {
    }

    public record RmaRequestListResponse(List<RmaRequestView> items) {
    }

    public record RmaRequestView(
            UUID id,
            String rmaNumber,
            UUID orderId,
            String customerEmail,
            String status,
            String reason,
            String desiredResolution,
            String managerComment,
            String decidedBy,
            OffsetDateTime decidedAt,
            int decisionVersion,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {
        public static RmaRequestView from(RmaRequest request) {
            return new RmaRequestView(
                    request.getId(),
                    request.getRmaNumber(),
                    request.getOrderId(),
                    request.getCustomerEmail(),
                    request.getStatus() != null ? request.getStatus().name() : null,
                    request.getReason(),
                    request.getDesiredResolution(),
                    request.getManagerComment(),
                    request.getDecidedBy(),
                    request.getDecidedAt(),
                    request.getDecisionVersion(),
                    request.getCreatedAt(),
                    request.getUpdatedAt()
            );
        }
    }

    public record ImportMapping(
            String sku,
            String productName,
            String productSlug,
            String variantName,
            String brandSlug,
            String categorySlug,
            String priceAmount,
            String stockQuantity,
            String currency
    ) {
    }

    public record ImportDryRunResponse(
            ImportJobView job,
            List<ImportRowView> rows
    ) {
    }

    public record ImportCommitResponse(
            ImportJobView job,
            int appliedRows
    ) {
    }

    public record ImportJobView(
            UUID id,
            String fileName,
            String status,
            int totalRows,
            int validRows,
            int invalidRows,
            String createdBy,
            OffsetDateTime committedAt,
            OffsetDateTime createdAt
    ) {
        public static ImportJobView from(CatalogueImportJob job) {
            return new ImportJobView(
                    job.getId(),
                    job.getFileName(),
                    job.getStatus(),
                    job.getTotalRows(),
                    job.getValidRows(),
                    job.getInvalidRows(),
                    job.getCreatedBy(),
                    job.getCommittedAt(),
                    job.getCreatedAt()
            );
        }
    }

    public record ImportRowView(
            UUID id,
            int rowNumber,
            String sku,
            String productName,
            String productSlug,
            String variantName,
            String brandSlug,
            String categorySlug,
            Long priceAmount,
            String currency,
            Integer stockQuantity,
            boolean valid,
            String errorMessage,
            Map<String, String> rawData
    ) {
    }

    public record PromotionRequest(
            String name,
            String type,
            String status,
            OffsetDateTime startsAt,
            OffsetDateTime endsAt,
            Integer discountPercent,
            Long discountAmount,
            Long salePriceAmount,
            String currency,
            Long thresholdAmount,
            String description,
            List<PromotionTargetRequest> targets
    ) {
    }

    public record PromotionTargetRequest(String targetKind, String targetKey) {
    }

    public record PromotionView(
            UUID id,
            String name,
            String type,
            String status,
            OffsetDateTime startsAt,
            OffsetDateTime endsAt,
            Integer discountPercent,
            Long discountAmount,
            Long salePriceAmount,
            String currency,
            Long thresholdAmount,
            String description,
            List<PromotionTargetView> targets,
            boolean activeNow
    ) {
    }

    public record PromotionTargetView(UUID id, String targetKind, String targetKey) {
    }

    public record PromoCodeRequest(
            String code,
            String status,
            Integer discountPercent,
            Long discountAmount,
            Long thresholdAmount,
            OffsetDateTime startsAt,
            OffsetDateTime endsAt,
            Integer maxRedemptions,
            String description
    ) {
    }

    public record PromoCodeView(
            UUID id,
            String code,
            String status,
            Integer discountPercent,
            Long discountAmount,
            Long thresholdAmount,
            OffsetDateTime startsAt,
            OffsetDateTime endsAt,
            Integer maxRedemptions,
            int redemptionCount,
            String description,
            boolean activeNow
    ) {
    }

    public record TaxConfigurationRequest(
            String name,
            String status,
            int taxSystemCode,
            int vatCode,
            BigDecimal vatRatePercent,
            boolean active
    ) {
    }

    public record TaxConfigurationView(
            UUID id,
            String name,
            String status,
            int taxSystemCode,
            int vatCode,
            BigDecimal vatRatePercent,
            boolean active,
            OffsetDateTime updatedAt
    ) {
    }

    public record ManagerAnalyticsResponse(List<ManagerAnalyticsRow> rows) {
    }

    public record ManagerAnalyticsRow(
            String managerSubject,
            long totalOrders,
            long paidOrders,
            Money paidAmount,
            Money commission
    ) {
    }

    public record PaymentLinkAnalyticsResponse(
            long sent,
            long paid,
            double conversionRate,
            List<PaymentLinkAnalyticsRow> rows
    ) {
    }

    public record PaymentLinkAnalyticsRow(
            UUID id,
            UUID orderId,
            String managerSubject,
            String managerEmail,
            String status,
            boolean paid,
            OffsetDateTime sentAt,
            OffsetDateTime createdAt
    ) {
    }

    public record LowStockAlertResponse(
            int threshold,
            List<LowStockRow> rows
    ) {
    }

    public record LowStockRow(
            UUID variantId,
            UUID productId,
            String productName,
            String productSlug,
            String variantName,
            String sku,
            int stock
    ) {
    }

    public record StockAlertSettingsRequest(int threshold) {
    }
}
