package com.example.api.admincms;

import com.example.api.admincms.DirectusAdminModels.ImportCommitResponse;
import com.example.api.admincms.DirectusAdminModels.ImportDryRunResponse;
import com.example.api.admincms.DirectusAdminModels.ImportJobView;
import com.example.api.admincms.DirectusAdminModels.ImportMapping;
import com.example.api.admincms.DirectusAdminModels.ImportReport;
import com.example.api.admincms.DirectusAdminModels.ImportRowView;
import com.example.api.admincms.DirectusAdminModels.LowStockAlertResponse;
import com.example.api.admincms.DirectusAdminModels.LowStockRow;
import com.example.api.admincms.DirectusAdminModels.ManagerAnalyticsResponse;
import com.example.api.admincms.DirectusAdminModels.ManagerAnalyticsRow;
import com.example.api.admincms.DirectusAdminModels.NotUpdatedVariantView;
import com.example.api.admincms.DirectusAdminModels.OrderDetail;
import com.example.api.admincms.DirectusAdminModels.OrderRefundRequest;
import com.example.api.admincms.DirectusAdminModels.OrderSearchResponse;
import com.example.api.admincms.DirectusAdminModels.OrderStatusEvent;
import com.example.api.admincms.DirectusAdminModels.PaymentLinkAnalyticsResponse;
import com.example.api.admincms.DirectusAdminModels.PaymentLinkAnalyticsRow;
import com.example.api.admincms.DirectusAdminModels.PromoCodeRequest;
import com.example.api.admincms.DirectusAdminModels.PromoCodeView;
import com.example.api.admincms.DirectusAdminModels.PromotionRequest;
import com.example.api.admincms.DirectusAdminModels.PromotionTargetRequest;
import com.example.api.admincms.DirectusAdminModels.PromotionTargetView;
import com.example.api.admincms.DirectusAdminModels.PromotionView;
import com.example.api.admincms.DirectusAdminModels.RmaDecisionRequest;
import com.example.api.admincms.DirectusAdminModels.RmaRequestCreateRequest;
import com.example.api.admincms.DirectusAdminModels.RmaRequestListResponse;
import com.example.api.admincms.DirectusAdminModels.RmaRequestView;
import com.example.api.admincms.DirectusAdminModels.ShipmentView;
import com.example.api.admincms.DirectusAdminModels.StockAlertSettingsRequest;
import com.example.api.admincms.DirectusAdminModels.TaxConfigurationRequest;
import com.example.api.admincms.DirectusAdminModels.TaxConfigurationView;
import com.example.api.catalog.DirectusBridgeSecurity;
import com.example.api.notification.NotificationOrchestrator;
import com.example.catalog.domain.Brand;
import com.example.catalog.domain.Product;
import com.example.catalog.domain.ProductVariant;
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
import com.example.payment.service.PaymentService;
import com.example.shipment.repository.ShipmentRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.transaction.Transactional;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional
public class DirectusAdminService {

    private static final TypeReference<Map<String, String>> STRING_MAP = new TypeReference<>() {
    };
    private static final Set<String> ORDER_STATUSES = Set.of(
            "PENDING",
            "PAID",
            "PROCESSING",
            "READY_FOR_PICKUP",
            "SHIPPED",
            "DELIVERED",
            "RECEIVED",
            "COMPLETED",
            "CANCELLED",
            "REFUNDED"
    );
    private static final Set<String> PICKER_VISIBLE_STATUSES = Set.of("PAID", "PROCESSING", "READY_FOR_PICKUP", "SHIPPED");
    private static final Set<String> PICKER_TARGET_STATUSES = Set.of("PROCESSING", "READY_FOR_PICKUP", "SHIPPED");
    private static final String STOCK_IMPORT_MODE = "STOCK_ONLY";
    private static final String SAMPLE_SKU_HEADER = "Номенклатура.Артикул";
    private static final String ATRIUM_STOCK_HEADER = "Склад 21 Век АТРИУМ.Остаток";
    private static final String IP_STOCK_HEADER = "Склад 21 Век ИП.Остаток";

    private final OrderRepository orderRepository;
    private final OrderService orderService;
    private final OrderStatusHistoryRepository orderStatusHistoryRepository;
    private final DirectusAdminRoleGuard roleGuard;
    private final CatalogueImportJobRepository importJobRepository;
    private final CatalogueImportRowRepository importRowRepository;
    private final PromotionRepository promotionRepository;
    private final PromoCodeRepository promoCodeRepository;
    private final TaxConfigurationRepository taxConfigurationRepository;
    private final ManagerPaymentLinkRepository managerPaymentLinkRepository;
    private final StockAlertSettingsRepository stockAlertSettingsRepository;
    private final ProductRepository productRepository;
    private final ProductVariantRepository variantRepository;
    private final CatalogService catalogService;
    private final InventoryService inventoryService;
    private final ObjectMapper objectMapper;
    private final RmaRequestRepository rmaRequestRepository;
    private final NotificationOrchestrator notificationOrchestrator;
    private final ShipmentRepository shipmentRepository;
    private final PaymentService paymentService;

    public DirectusAdminService(
            OrderRepository orderRepository,
            OrderService orderService,
            OrderStatusHistoryRepository orderStatusHistoryRepository,
            DirectusAdminRoleGuard roleGuard,
            CatalogueImportJobRepository importJobRepository,
            CatalogueImportRowRepository importRowRepository,
            PromotionRepository promotionRepository,
            PromoCodeRepository promoCodeRepository,
            TaxConfigurationRepository taxConfigurationRepository,
            ManagerPaymentLinkRepository managerPaymentLinkRepository,
            StockAlertSettingsRepository stockAlertSettingsRepository,
            ProductRepository productRepository,
            ProductVariantRepository variantRepository,
            CatalogService catalogService,
            InventoryService inventoryService,
            ObjectMapper objectMapper
    ) {
        this(
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
                objectMapper,
                null,
                null,
                null,
                null
        );
    }

    public DirectusAdminService(
            OrderRepository orderRepository,
            OrderService orderService,
            OrderStatusHistoryRepository orderStatusHistoryRepository,
            DirectusAdminRoleGuard roleGuard,
            CatalogueImportJobRepository importJobRepository,
            CatalogueImportRowRepository importRowRepository,
            PromotionRepository promotionRepository,
            PromoCodeRepository promoCodeRepository,
            TaxConfigurationRepository taxConfigurationRepository,
            ManagerPaymentLinkRepository managerPaymentLinkRepository,
            StockAlertSettingsRepository stockAlertSettingsRepository,
            ProductRepository productRepository,
            ProductVariantRepository variantRepository,
            CatalogService catalogService,
            InventoryService inventoryService,
            ObjectMapper objectMapper,
            RmaRequestRepository rmaRequestRepository,
            NotificationOrchestrator notificationOrchestrator,
            ShipmentRepository shipmentRepository
    ) {
        this(
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
                objectMapper,
                rmaRequestRepository,
                notificationOrchestrator,
                shipmentRepository,
                null
        );
    }

    @Autowired
    public DirectusAdminService(
            OrderRepository orderRepository,
            OrderService orderService,
            OrderStatusHistoryRepository orderStatusHistoryRepository,
            DirectusAdminRoleGuard roleGuard,
            CatalogueImportJobRepository importJobRepository,
            CatalogueImportRowRepository importRowRepository,
            PromotionRepository promotionRepository,
            PromoCodeRepository promoCodeRepository,
            TaxConfigurationRepository taxConfigurationRepository,
            ManagerPaymentLinkRepository managerPaymentLinkRepository,
            StockAlertSettingsRepository stockAlertSettingsRepository,
            ProductRepository productRepository,
            ProductVariantRepository variantRepository,
            CatalogService catalogService,
            InventoryService inventoryService,
            ObjectMapper objectMapper,
            RmaRequestRepository rmaRequestRepository,
            NotificationOrchestrator notificationOrchestrator,
            ShipmentRepository shipmentRepository,
            PaymentService paymentService
    ) {
        this.orderRepository = orderRepository;
        this.orderService = orderService;
        this.orderStatusHistoryRepository = orderStatusHistoryRepository;
        this.roleGuard = roleGuard;
        this.importJobRepository = importJobRepository;
        this.importRowRepository = importRowRepository;
        this.promotionRepository = promotionRepository;
        this.promoCodeRepository = promoCodeRepository;
        this.taxConfigurationRepository = taxConfigurationRepository;
        this.managerPaymentLinkRepository = managerPaymentLinkRepository;
        this.stockAlertSettingsRepository = stockAlertSettingsRepository;
        this.productRepository = productRepository;
        this.variantRepository = variantRepository;
        this.catalogService = catalogService;
        this.inventoryService = inventoryService;
        this.objectMapper = objectMapper;
        this.rmaRequestRepository = rmaRequestRepository;
        this.notificationOrchestrator = notificationOrchestrator;
        this.shipmentRepository = shipmentRepository;
        this.paymentService = paymentService;
    }

    public OrderSearchResponse searchOrders(String status,
                                            String manager,
                                            OffsetDateTime from,
                                            OffsetDateTime to,
                                            String query,
                                            String archived,
                                            DirectusBridgeSecurity.DirectusBridgePrincipal principal) {
        String normalizedStatus = normalize(status);
        String normalizedManager = normalize(manager);
        String normalizedQuery = normalize(query);
        String archiveMode = resolveArchiveMode(archived, principal);
        List<DirectusAdminModels.OrderSummary> items = orderRepository.findAllByOrderByOrderDateDesc().stream()
                .filter(order -> canReadOrder(order, principal))
                .filter(order -> archiveModeMatches(order, archiveMode))
                .filter(order -> !StringUtils.hasText(normalizedStatus) || statusMatches(order.getStatus(), normalizedStatus))
                .filter(order -> !StringUtils.hasText(normalizedManager) || matchesManager(order, normalizedManager))
                .filter(order -> from == null || (order.getOrderDate() != null && !order.getOrderDate().isBefore(from)))
                .filter(order -> to == null || (order.getOrderDate() != null && !order.getOrderDate().isAfter(to)))
                .filter(order -> !StringUtils.hasText(normalizedQuery)
                        || containsIgnoreCase(String.valueOf(order.getId()), normalizedQuery)
                        || containsIgnoreCase(order.getReceiptEmail(), normalizedQuery)
                        || containsIgnoreCase(order.getPublicToken(), normalizedQuery)
                        || matchesManager(order, normalizedQuery))
                .map(DirectusAdminModels.OrderSummary::from)
                .toList();
        return new OrderSearchResponse(items);
    }

    public OrderDetail getOrder(UUID orderId, DirectusBridgeSecurity.DirectusBridgePrincipal principal) {
        Order order = orderService.findById(orderId);
        requireCanReadOrder(order, principal);
        return toOrderDetail(order);
    }

    public OrderDetail updateOrderStatus(UUID orderId,
                                         String status,
                                         String note,
                                         DirectusBridgeSecurity.DirectusBridgePrincipal principal) {
        Order before = orderService.findById(orderId);
        requireActiveOrder(before);
        String nextStatus = normalizeStatusAlias(requireText(status, "status"));
        if (!ORDER_STATUSES.contains(nextStatus)) {
            throw new IllegalArgumentException("Unsupported order status: " + status);
        }
        requireCanUpdateOrder(before, nextStatus, principal);
        Order updated = orderService.updateOrderStatus(orderId, nextStatus);
        OrderStatusHistory event = new OrderStatusHistory();
        event.setOrderId(orderId);
        event.setPreviousStatus(before.getStatus());
        event.setNextStatus(updated.getStatus());
        event.setActor(principal.actor());
        event.setActorRole(principal.primaryRole());
        event.setNote(normalize(note));
        orderStatusHistoryRepository.save(event);
        if (notificationOrchestrator != null) {
            notificationOrchestrator.orderStatusChanged(updated, before.getStatus());
        }
        return toOrderDetail(updated);
    }

    public OrderDetail claimOrder(UUID orderId, DirectusBridgeSecurity.DirectusBridgePrincipal principal) {
        Order order = orderService.findById(orderId);
        requireActiveOrder(order);
        requireCanClaimOrder(order, principal);
        if (isAssignedToPrincipal(order, principal)) {
            return toOrderDetail(order);
        }
        if (isAssigned(order)) {
            throw new IllegalStateException("Order is already assigned to " + assignedManagerLabel(order));
        }
        String managerEmail = principal.email();
        String managerLabel = StringUtils.hasText(managerEmail) ? managerEmail : principal.actor();
        OffsetDateTime claimedAt = OffsetDateTime.now();
        int claimed = orderRepository.claimDirectusManager(orderId, managerLabel, managerEmail, principal.userId(), claimedAt);
        order = orderService.findById(orderId);
        if (claimed == 0) {
            if (isAssignedToPrincipal(order, principal)) {
                return toOrderDetail(order);
            }
            if (isAssigned(order)) {
                throw new IllegalStateException("Order is already assigned to " + assignedManagerLabel(order));
            }
            throw new IllegalStateException("Order could not be claimed. Please reload and try again.");
        }
        OrderStatusHistory event = new OrderStatusHistory();
        event.setOrderId(orderId);
        event.setPreviousStatus(order.getStatus());
        event.setNextStatus(order.getStatus());
        event.setActor(principal.actor());
        event.setActorRole(principal.primaryRole());
        event.setNote("claimed");
        orderStatusHistoryRepository.save(event);
        return toOrderDetail(order);
    }

    public OrderDetail clearOrderClaim(UUID orderId, DirectusBridgeSecurity.DirectusBridgePrincipal principal) {
        if (!roleGuard.isAdmin(principal)) {
            throw new AccessDeniedException("Only administrators can clear order assignment");
        }
        Order order = orderService.findById(orderId);
        requireActiveOrder(order);
        if (!isAssigned(order)) {
            return toOrderDetail(order);
        }
        order.setManagerSubject(null);
        order.setManagerEmail(null);
        order.setManagerDirectusUserId(null);
        order.setManagerClaimedAt(null);
        order = orderRepository.save(order);

        OrderStatusHistory event = new OrderStatusHistory();
        event.setOrderId(orderId);
        event.setPreviousStatus(order.getStatus());
        event.setNextStatus(order.getStatus());
        event.setActor(principal.actor());
        event.setActorRole(principal.primaryRole());
        event.setNote("assignment-cleared");
        orderStatusHistoryRepository.save(event);
        return toOrderDetail(order);
    }

    public OrderDetail archiveOrder(UUID orderId,
                                    String reason,
                                    DirectusBridgeSecurity.DirectusBridgePrincipal principal) {
        if (!roleGuard.isAdmin(principal)) {
            throw new AccessDeniedException("Only administrators can archive orders");
        }
        Order order = orderService.findById(orderId);
        if (isArchived(order)) {
            return toOrderDetail(order);
        }
        order.setArchivedAt(OffsetDateTime.now());
        order.setArchivedBy(principal.actor());
        order.setArchiveReason(StringUtils.hasText(reason) ? reason.trim() : "Archived from Directus");
        order = orderRepository.save(order);

        OrderStatusHistory event = new OrderStatusHistory();
        event.setOrderId(orderId);
        event.setPreviousStatus(order.getStatus());
        event.setNextStatus(order.getStatus());
        event.setActor(principal.actor());
        event.setActorRole(principal.primaryRole());
        event.setNote("archived");
        orderStatusHistoryRepository.save(event);
        return toOrderDetail(order);
    }

    public OrderDetail restoreOrder(UUID orderId, DirectusBridgeSecurity.DirectusBridgePrincipal principal) {
        if (!roleGuard.isAdmin(principal)) {
            throw new AccessDeniedException("Only administrators can restore orders");
        }
        Order order = orderService.findById(orderId);
        if (!isArchived(order)) {
            return toOrderDetail(order);
        }
        order.setArchivedAt(null);
        order.setArchivedBy(null);
        order.setArchiveReason(null);
        order = orderRepository.save(order);

        OrderStatusHistory event = new OrderStatusHistory();
        event.setOrderId(orderId);
        event.setPreviousStatus(order.getStatus());
        event.setNextStatus(order.getStatus());
        event.setActor(principal.actor());
        event.setActorRole(principal.primaryRole());
        event.setNote("archive-restored");
        orderStatusHistoryRepository.save(event);
        return toOrderDetail(order);
    }

    public OrderDetail refundOrder(UUID orderId,
                                   OrderRefundRequest request,
                                   DirectusBridgeSecurity.DirectusBridgePrincipal principal) {
        if (!roleGuard.isAdmin(principal)) {
            throw new AccessDeniedException("Only administrators can create payment refunds");
        }
        if (paymentService == null) {
            throw new IllegalStateException("Payment service is not available");
        }
        Order order = orderService.findById(orderId);
        requireActiveOrder(order);
        requireCanReadOrder(order, principal);
        List<PaymentService.RefundLineRequest> lines = request != null && request.items() != null
                ? request.items().stream()
                    .map(item -> new PaymentService.RefundLineRequest(
                            item.orderItemId(),
                            item.quantity(),
                            item.amount()
                    ))
                    .toList()
                : List.of();
        paymentService.refundYooKassaPayment(orderId, lines);

        OrderStatusHistory event = new OrderStatusHistory();
        event.setOrderId(orderId);
        event.setPreviousStatus(order.getStatus());
        event.setNextStatus(order.getStatus());
        event.setActor(principal.actor());
        event.setActorRole(principal.primaryRole());
        event.setNote("payment-refund");
        orderStatusHistoryRepository.save(event);
        return toOrderDetail(orderService.findById(orderId));
    }

    private OrderDetail toOrderDetail(Order order) {
        attachPaymentSummary(order);
        return new OrderDetail(
                order,
                orderStatusHistoryRepository.findByOrderIdOrderByCreatedAtAsc(order.getId()).stream()
                        .map(OrderStatusEvent::from)
                        .toList(),
                shipmentView(order),
                rmaRequestViews(order.getId())
        );
    }

    private void attachPaymentSummary(Order order) {
        if (paymentService != null && order != null && order.getId() != null) {
            order.setPaymentSummary(paymentService.getPaymentSummary(order.getId()));
        }
    }

    private ShipmentView shipmentView(Order order) {
        if (shipmentRepository == null || order == null || order.getShipmentId() == null) {
            return null;
        }
        return shipmentRepository.findById(order.getShipmentId())
                .map(ShipmentView::from)
                .orElse(null);
    }

    private List<RmaRequestView> rmaRequestViews(UUID orderId) {
        if (rmaRequestRepository == null || orderId == null) {
            return List.of();
        }
        return rmaRequestRepository.findByOrderIdOrderByCreatedAtDesc(orderId).stream()
                .map(RmaRequestView::from)
                .toList();
    }

    public RmaRequestListResponse listRmaRequests(String status,
                                                  UUID orderId,
                                                  DirectusBridgeSecurity.DirectusBridgePrincipal principal) {
        RmaRequestRepository repository = requireRmaRepository();
        RmaStatus filterStatus = parseRmaStatus(status, false);
        if (orderId != null) {
            Order order = orderService.findById(orderId);
            requireCanReadOrder(order, principal);
        }
        List<RmaRequestView> items = (filterStatus != null
                ? repository.findByStatusOrderByCreatedAtDesc(filterStatus)
                : repository.findAllNewestFirst()).stream()
                .filter(request -> orderId == null || orderId.equals(request.getOrderId()))
                .filter(request -> canReadRmaRequest(request, principal))
                .map(RmaRequestView::from)
                .toList();
        return new RmaRequestListResponse(items);
    }

    public RmaRequestView createRmaRequest(RmaRequestCreateRequest request,
                                           DirectusBridgeSecurity.DirectusBridgePrincipal principal) {
        RmaRequestRepository repository = requireRmaRepository();
        UUID orderId = request != null ? request.orderId() : null;
        if (orderId == null) {
            throw new IllegalArgumentException("orderId is required");
        }
        Order order = orderService.findById(orderId);
        requireCanReadOrder(order, principal);
        requireActiveOrder(order);

        RmaRequest rma = new RmaRequest();
        rma.setRmaNumber(nextRmaNumber(repository));
        rma.setOrderId(order.getId());
        rma.setCustomerEmail(order.getReceiptEmail());
        rma.setStatus(RmaStatus.REQUESTED);
        rma.setReason(normalize(request != null ? request.reason() : null));
        rma.setDesiredResolution(normalize(request != null ? request.desiredResolution() : null));
        return RmaRequestView.from(repository.save(rma));
    }

    public RmaRequestView decideRmaRequest(UUID rmaId,
                                           RmaDecisionRequest request,
                                           DirectusBridgeSecurity.DirectusBridgePrincipal principal) {
        RmaRequestRepository repository = requireRmaRepository();
        RmaRequest rma = repository.findById(rmaId)
                .orElseThrow(() -> new IllegalArgumentException("RMA request not found: " + rmaId));
        Order order = orderService.findById(rma.getOrderId());
        requireActiveOrder(order);
        requireCanDecideRma(order, principal);
        RmaStatus nextStatus = parseRmaStatus(request != null ? request.status() : null, true);
        if (nextStatus == RmaStatus.REQUESTED) {
            throw new IllegalArgumentException("RMA decision status must be APPROVED or REJECTED");
        }
        String nextComment = normalize(request != null ? request.comment() : null);
        boolean changed = rma.getStatus() != nextStatus || !Objects.equals(normalize(rma.getManagerComment()), nextComment);
        if (!changed) {
            return RmaRequestView.from(rma);
        }
        rma.setStatus(nextStatus);
        rma.setManagerComment(nextComment);
        rma.setDecidedBy(principal.actor());
        rma.setDecidedAt(OffsetDateTime.now());
        rma.setDecisionVersion(rma.getDecisionVersion() + 1);
        rma = repository.save(rma);
        if (notificationOrchestrator != null) {
            notificationOrchestrator.rmaDecision(
                    order,
                    rma.getId(),
                    rma.getRmaNumber(),
                    rma.getDecisionVersion(),
                    rma.getStatus().name(),
                    rma.getManagerComment()
            );
        }
        return RmaRequestView.from(rma);
    }

    private RmaRequestRepository requireRmaRepository() {
        if (rmaRequestRepository == null) {
            throw new IllegalStateException("RMA repository is not available");
        }
        return rmaRequestRepository;
    }

    private boolean canReadRmaRequest(RmaRequest request, DirectusBridgeSecurity.DirectusBridgePrincipal principal) {
        if (request == null || request.getOrderId() == null) {
            return false;
        }
        return orderRepository.findById(request.getOrderId())
                .map(order -> canReadOrder(order, principal))
                .orElse(false);
    }

    private void requireCanDecideRma(Order order, DirectusBridgeSecurity.DirectusBridgePrincipal principal) {
        if (roleGuard.isAdmin(principal)) {
            return;
        }
        if (roleGuard.isManager(principal) && isAssignedToPrincipal(order, principal)) {
            return;
        }
        throw new AccessDeniedException("Directus role is not allowed to decide this RMA request");
    }

    private RmaStatus parseRmaStatus(String status, boolean required) {
        if (!StringUtils.hasText(status)) {
            if (required) {
                throw new IllegalArgumentException("RMA status is required");
            }
            return null;
        }
        try {
            return RmaStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Unsupported RMA status: " + status);
        }
    }

    private String nextRmaNumber(RmaRequestRepository repository) {
        for (int attempt = 0; attempt < 5; attempt++) {
            String candidate = "RMA-" + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase(Locale.ROOT);
            if (!repository.existsByRmaNumber(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Could not generate unique RMA number");
    }

    private void requireCanReadOrder(Order order, DirectusBridgeSecurity.DirectusBridgePrincipal principal) {
        if (!canReadOrder(order, principal)) {
            throw new AccessDeniedException("Directus role is not allowed to access this order");
        }
    }

    private boolean canReadOrder(Order order, DirectusBridgeSecurity.DirectusBridgePrincipal principal) {
        if (roleGuard.isAdmin(principal)) {
            return true;
        }
        if (isArchived(order)) {
            return false;
        }
        if (roleGuard.isManager(principal)) {
            return !isAssigned(order) || isAssignedToPrincipal(order, principal);
        }
        if (roleGuard.isPicker(principal)) {
            return isPickerQueueOrder(order);
        }
        return false;
    }

    private void requireActiveOrder(Order order) {
        if (isArchived(order)) {
            throw new IllegalStateException("Archived orders are read-only. Restore the order before changing it.");
        }
    }

    private boolean isArchived(Order order) {
        return order != null && order.getArchivedAt() != null;
    }

    private String resolveArchiveMode(String archived, DirectusBridgeSecurity.DirectusBridgePrincipal principal) {
        String mode = normalize(archived);
        if (!StringUtils.hasText(mode)) {
            return roleGuard.isAdmin(principal) ? "all" : "active";
        }
        String normalized = mode.toLowerCase(Locale.ROOT);
        if (!Set.of("all", "active", "archived").contains(normalized)) {
            throw new IllegalArgumentException("Unsupported archived filter: " + archived);
        }
        if (!roleGuard.isAdmin(principal) && !"active".equals(normalized)) {
            return "active";
        }
        return normalized;
    }

    private boolean archiveModeMatches(Order order, String archiveMode) {
        if ("all".equals(archiveMode)) {
            return true;
        }
        boolean archived = isArchived(order);
        if ("archived".equals(archiveMode)) {
            return archived;
        }
        return !archived;
    }

    private void requireCanUpdateOrder(Order order,
                                       String nextStatus,
                                       DirectusBridgeSecurity.DirectusBridgePrincipal principal) {
        if (roleGuard.isAdmin(principal)) {
            return;
        }
        if (roleGuard.isManager(principal)) {
            if (isAssignedToPrincipal(order, principal)) {
                return;
            }
            throw new AccessDeniedException("Claim this order before changing its status");
        }
        if (roleGuard.isPicker(principal)) {
            if (isPickerQueueOrder(order) && PICKER_TARGET_STATUSES.contains(nextStatus)) {
                return;
            }
            throw new AccessDeniedException("Picker can only advance paid order queue statuses");
        }
        throw new AccessDeniedException("Directus role is not allowed to update this order");
    }

    private void requireCanClaimOrder(Order order, DirectusBridgeSecurity.DirectusBridgePrincipal principal) {
        if (roleGuard.isPicker(principal)) {
            throw new AccessDeniedException("Picker role cannot claim manager ownership of orders");
        }
        if (roleGuard.isAdmin(principal) || roleGuard.isManager(principal)) {
            return;
        }
        throw new AccessDeniedException("Directus role is not allowed to claim orders");
    }

    private boolean isPickerQueueOrder(Order order) {
        return order != null && PICKER_VISIBLE_STATUSES.contains(normalizeStatus(order.getStatus()));
    }

    private boolean isAssigned(Order order) {
        return order != null
                && (StringUtils.hasText(order.getManagerDirectusUserId())
                || StringUtils.hasText(order.getManagerEmail())
                || StringUtils.hasText(order.getManagerSubject()));
    }

    private boolean isAssignedToPrincipal(Order order, DirectusBridgeSecurity.DirectusBridgePrincipal principal) {
        if (order == null || principal == null) {
            return false;
        }
        if (StringUtils.hasText(principal.userId())
                && equalsIgnoreCase(order.getManagerDirectusUserId(), principal.userId())) {
            return true;
        }
        if (StringUtils.hasText(principal.email())) {
            return equalsIgnoreCase(order.getManagerEmail(), principal.email())
                    || equalsIgnoreCase(order.getManagerSubject(), principal.email());
        }
        return StringUtils.hasText(principal.externalId())
                && equalsIgnoreCase(order.getManagerSubject(), principal.externalId());
    }

    private boolean matchesManager(Order order, String query) {
        return containsIgnoreCase(order.getManagerSubject(), query)
                || containsIgnoreCase(order.getManagerEmail(), query)
                || containsIgnoreCase(order.getManagerDirectusUserId(), query);
    }

    private String assignedManagerLabel(Order order) {
        if (order == null) {
            return "another account";
        }
        if (StringUtils.hasText(order.getManagerEmail())) {
            return order.getManagerEmail();
        }
        if (StringUtils.hasText(order.getManagerSubject())) {
            return order.getManagerSubject();
        }
        if (StringUtils.hasText(order.getManagerDirectusUserId())) {
            return order.getManagerDirectusUserId();
        }
        return "another account";
    }

    private String normalizeStatus(String status) {
        return StringUtils.hasText(status) ? status.trim().toUpperCase(Locale.ROOT) : "";
    }

    private String normalizeStatusAlias(String status) {
        String normalized = normalizeStatus(status);
        return "COMPLETED".equals(normalized) ? "RECEIVED" : normalized;
    }

    private boolean statusMatches(String actual, String requested) {
        String normalizedActual = normalizeStatusAlias(actual);
        String normalizedRequested = normalizeStatusAlias(requested);
        return StringUtils.hasText(normalizedRequested) && normalizedActual.equals(normalizedRequested);
    }

    public ImportDryRunResponse dryRunImport(MultipartFile file, ImportMapping mapping, DirectusBridgeSecurity.DirectusBridgePrincipal principal) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Import file is required");
        }
        ImportMapping resolvedMapping = resolveMapping(mapping);
        List<Map<String, String>> sourceRows = readRows(file, resolvedMapping);
        CatalogueImportJob job = new CatalogueImportJob();
        job.setFileName(file.getOriginalFilename());
        job.setStatus("DRY_RUN");
        job.setMode(STOCK_IMPORT_MODE);
        job.setCreatedBy(principal.actor());
        job.setTotalRows(sourceRows.size());

        List<CatalogueImportRow> rows = new ArrayList<>();
        int valid = 0;
        int invalid = 0;
        for (int index = 0; index < sourceRows.size(); index++) {
            Map<String, String> sourceRow = sourceRows.get(index);
            Integer sourceRowNumber = parseInteger(sourceRow.get("__rowNumber"));
            CatalogueImportRow row = toImportRow(sourceRowNumber != null ? sourceRowNumber : index + 2, sourceRow, resolvedMapping);
            rows.add(row);
        }
        markDuplicateSkus(rows);
        for (CatalogueImportRow row : rows) {
            if (row.isValid()) {
                valid++;
            } else {
                invalid++;
            }
        }
        job.setValidRows(valid);
        job.setInvalidRows(invalid);
        job = importJobRepository.save(job);
        for (CatalogueImportRow row : rows) {
            row.setJobId(job.getId());
        }
        rows = importRowRepository.saveAll(rows);
        ImportReport report = buildImportReport(rows);
        return new ImportDryRunResponse(ImportJobView.from(job), rows.stream().map(this::toImportRowView).toList(), report);
    }

    public ImportCommitResponse commitImport(UUID jobId) {
        CatalogueImportJob job = importJobRepository.findById(jobId)
                .orElseThrow(() -> new IllegalArgumentException("Import job not found: " + jobId));
        if ("COMMITTED".equalsIgnoreCase(job.getStatus())) {
            List<CatalogueImportRow> rows = importRowRepository.findByJobIdOrderByRowNumberAsc(jobId);
            return new ImportCommitResponse(ImportJobView.from(job), appliedRows(rows), buildImportReport(rows));
        }
        if (job.getInvalidRows() > 0) {
            throw new IllegalStateException("Import has invalid rows and cannot be committed");
        }

        List<CatalogueImportRow> rows = importRowRepository.findByJobIdOrderByRowNumberAsc(jobId);
        ImportReport reportBeforeCommit = buildImportReport(rows);
        int applied = 0;
        for (CatalogueImportRow row : rows) {
            if (!row.isValid()) {
                continue;
            }
            if (applyImportRow(row)) {
                applied++;
            }
        }
        job.setStatus("COMMITTED");
        job.setCommittedAt(OffsetDateTime.now());
        job = importJobRepository.save(job);
        return new ImportCommitResponse(ImportJobView.from(job), applied, reportBeforeCommit);
    }

    public List<ImportJobView> listImports() {
        return importJobRepository.findTop25ByOrderByCreatedAtDesc().stream().map(ImportJobView::from).toList();
    }

    public ImportReport importReport(UUID jobId) {
        if (!importJobRepository.existsById(jobId)) {
            throw new IllegalArgumentException("Import job not found: " + jobId);
        }
        return buildImportReport(importRowRepository.findByJobIdOrderByRowNumberAsc(jobId));
    }

    public byte[] notUpdatedImportReportText(UUID jobId) {
        ImportReport report = importReport(jobId);
        StringBuilder builder = new StringBuilder();
        builder.append("sku\tproduct_name\tproduct_slug\tvariant_name\tcurrent_stock\tproduct_active\n");
        for (NotUpdatedVariantView row : report.notUpdatedVariants()) {
            builder
                    .append(textCell(row.sku())).append('\t')
                    .append(textCell(row.productName())).append('\t')
                    .append(textCell(row.productSlug())).append('\t')
                    .append(textCell(row.variantName())).append('\t')
                    .append(row.currentStock()).append('\t')
                    .append(row.productActive())
                    .append('\n');
        }
        return builder.toString().getBytes(StandardCharsets.UTF_8);
    }

    public byte[] notUpdatedImportReportWorkbook(UUID jobId) {
        ImportReport report = importReport(jobId);
        try (var workbook = new XSSFWorkbook(); var out = new ByteArrayOutputStream()) {
            org.apache.poi.ss.usermodel.Sheet sheet = workbook.createSheet("Not updated");
            Row header = sheet.createRow(0);
            String[] headers = {"SKU", "Product name", "Product slug", "Variant name", "Current stock", "Product active"};
            for (int column = 0; column < headers.length; column++) {
                header.createCell(column).setCellValue(headers[column]);
            }
            int rowIndex = 1;
            for (NotUpdatedVariantView row : report.notUpdatedVariants()) {
                Row sheetRow = sheet.createRow(rowIndex++);
                sheetRow.createCell(0).setCellValue(textCell(row.sku()));
                sheetRow.createCell(1).setCellValue(textCell(row.productName()));
                sheetRow.createCell(2).setCellValue(textCell(row.productSlug()));
                sheetRow.createCell(3).setCellValue(textCell(row.variantName()));
                sheetRow.createCell(4).setCellValue(row.currentStock());
                sheetRow.createCell(5).setCellValue(row.productActive());
            }
            for (int column = 0; column < headers.length; column++) {
                sheet.autoSizeColumn(column);
            }
            workbook.write(out);
            return out.toByteArray();
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to build not-updated import workbook", ex);
        }
    }

    public List<PromotionView> listPromotions() {
        return promotionRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt")).stream()
                .map(this::toPromotionView)
                .toList();
    }

    public PromotionView getPromotion(UUID id) {
        return promotionRepository.findById(id)
                .map(this::toPromotionView)
                .orElseThrow(() -> new IllegalArgumentException("Promotion not found: " + id));
    }

    public List<PromotionView> activePromotions() {
        return promotionRepository.findByStatusIgnoreCase("ACTIVE").stream()
                .filter(this::isActiveNow)
                .map(this::toPromotionView)
                .toList();
    }

    public PromotionView savePromotion(UUID id, PromotionRequest request) {
        Promotion promotion = id != null
                ? promotionRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Promotion not found: " + id))
                : new Promotion();
        promotion.setName(requireText(request.name(), "name"));
        String promotionType = defaultText(request.type(), "PRODUCT_SALE").toUpperCase(Locale.ROOT);
        if (!"PRODUCT_SALE".equals(promotionType)) {
            throw new IllegalArgumentException("Only PRODUCT_SALE promotions are editable; cart thresholds are fixed by DSC-01");
        }
        promotion.setType(promotionType);
        promotion.setStatus(defaultText(request.status(), "ACTIVE").toUpperCase(Locale.ROOT));
        promotion.setStartsAt(request.startsAt());
        promotion.setEndsAt(request.endsAt());
        promotion.setDiscountPercent(request.discountPercent());
        promotion.setDiscountAmount(nonNegative(request.discountAmount()));
        promotion.setSalePriceAmount(nonNegative(request.salePriceAmount()));
        promotion.setCurrency(defaultText(request.currency(), "RUB").toUpperCase(Locale.ROOT));
        promotion.setThresholdAmount(nonNegative(request.thresholdAmount()));
        promotion.setDescription(normalize(request.description()));
        promotion.getTargets().clear();
        if (request.targets() != null) {
            request.targets().stream()
                    .filter(target -> StringUtils.hasText(target.targetKind()) && StringUtils.hasText(target.targetKey()))
                    .map(this::toPromotionTarget)
                    .forEach(promotion::addTarget);
        }
        return toPromotionView(promotionRepository.save(promotion));
    }

    public void deletePromotion(UUID id) {
        promotionRepository.deleteById(id);
    }

    public List<PromoCodeView> listPromoCodes() {
        return promoCodeRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt")).stream()
                .map(this::toPromoCodeView)
                .toList();
    }

    public PromoCodeView getPromoCode(UUID id) {
        return promoCodeRepository.findById(id)
                .map(this::toPromoCodeView)
                .orElseThrow(() -> new IllegalArgumentException("Promo code not found: " + id));
    }

    public PromoCodeView savePromoCode(UUID id, PromoCodeRequest request) {
        PromoCode promoCode = id != null
                ? promoCodeRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Promo code not found: " + id))
                : new PromoCode();
        promoCode.setCode(requireText(request.code(), "code").trim().toUpperCase(Locale.ROOT));
        promoCode.setStatus(defaultText(request.status(), "ACTIVE").toUpperCase(Locale.ROOT));
        promoCode.setDiscountPercent(request.discountPercent());
        promoCode.setDiscountAmount(nonNegative(request.discountAmount()));
        promoCode.setThresholdAmount(nonNegative(request.thresholdAmount()));
        promoCode.setStartsAt(request.startsAt());
        promoCode.setEndsAt(request.endsAt());
        promoCode.setMaxRedemptions(request.maxRedemptions() != null && request.maxRedemptions() > 0 ? request.maxRedemptions() : null);
        promoCode.setDescription(normalize(request.description()));
        return toPromoCodeView(promoCodeRepository.save(promoCode));
    }

    public void deletePromoCode(UUID id) {
        promoCodeRepository.deleteById(id);
    }

    public PromoCodeView validatePromoCode(String code, long subtotalAmount) {
        String normalizedCode = requireText(code, "code").trim().toUpperCase(Locale.ROOT);
        PromoCode promoCode = promoCodeRepository.findByCodeIgnoreCase(normalizedCode)
                .orElseThrow(() -> new IllegalArgumentException("Promo code not found: " + normalizedCode));
        if (!isActiveNow(promoCode.getStatus(), promoCode.getStartsAt(), promoCode.getEndsAt())) {
            throw new IllegalStateException("Promo code is not active");
        }
        if (promoCode.getMaxRedemptions() != null && promoCode.getRedemptionCount() >= promoCode.getMaxRedemptions()) {
            throw new IllegalStateException("Promo code redemption limit reached");
        }
        if (promoCode.getThresholdAmount() != null && subtotalAmount < promoCode.getThresholdAmount()) {
            throw new IllegalStateException("Cart total is below promo code threshold");
        }
        return toPromoCodeView(promoCode);
    }

    public List<TaxConfigurationView> listTaxConfigurations() {
        return taxConfigurationRepository.findAll(Sort.by(Sort.Direction.DESC, "active").and(Sort.by("name"))).stream()
                .map(this::toTaxConfigurationView)
                .toList();
    }

    public TaxConfigurationView getTaxConfiguration(UUID id) {
        return taxConfigurationRepository.findById(id)
                .map(this::toTaxConfigurationView)
                .orElseThrow(() -> new IllegalArgumentException("Tax configuration not found: " + id));
    }

    public TaxConfigurationView saveTaxConfiguration(UUID id, TaxConfigurationRequest request) {
        TaxConfiguration config = id != null
                ? taxConfigurationRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Tax configuration not found: " + id))
                : new TaxConfiguration();
        config.setName(requireText(request.name(), "name"));
        config.setStatus(defaultText(request.status(), "ACTIVE").toUpperCase(Locale.ROOT));
        config.setTaxSystemCode(request.taxSystemCode());
        config.setVatCode(request.vatCode());
        config.setVatRatePercent(request.vatRatePercent());
        if (request.active()) {
            taxConfigurationRepository.findAll().forEach(existing -> {
                if (!existing.getId().equals(config.getId())) {
                    existing.setActive(false);
                    taxConfigurationRepository.save(existing);
                }
            });
        }
        config.setActive(request.active());
        return toTaxConfigurationView(taxConfigurationRepository.save(config));
    }

    public Optional<TaxConfiguration> activeTaxConfiguration() {
        return taxConfigurationRepository.findFirstByActiveTrueAndStatusIgnoreCase("ACTIVE");
    }

    public Optional<TaxConfigurationView> activeTaxConfigurationView() {
        return activeTaxConfiguration().map(this::toTaxConfigurationView);
    }

    public void deleteTaxConfiguration(UUID id) {
        taxConfigurationRepository.deleteById(id);
    }

    public void recordManagerPaymentLink(Order order, String managerSubject, String managerEmail, boolean sent) {
        ManagerPaymentLink link = new ManagerPaymentLink();
        link.setOrderId(order.getId());
        link.setManagerSubject(managerSubject);
        link.setManagerEmail(managerEmail);
        link.setPublicToken(order.getPublicToken());
        link.setStatus(sent ? "SENT" : "CREATED");
        if (sent) {
            link.setSentAt(OffsetDateTime.now());
        }
        managerPaymentLinkRepository.save(link);
    }

    public ManagerAnalyticsResponse managerAnalytics(OffsetDateTime from, OffsetDateTime to, String manager) {
        String normalizedManager = normalize(manager);
        Set<UUID> managerLinkedOrderIds = managerLinkedOrderIds(normalizedManager);
        Map<String, List<Order>> byManager = orderRepository.findAllByOrderByOrderDateDesc().stream()
                .filter(this::isAssigned)
                .filter(order -> !StringUtils.hasText(normalizedManager)
                        || matchesManager(order, normalizedManager)
                        || managerLinkedOrderIds.contains(order.getId()))
                .filter(order -> from == null || (order.getOrderDate() != null && !order.getOrderDate().isBefore(from)))
                .filter(order -> to == null || (order.getOrderDate() != null && !order.getOrderDate().isAfter(to)))
                .collect(Collectors.groupingBy(this::assignedManagerLabel, LinkedHashMap::new, Collectors.toList()));
        List<ManagerAnalyticsRow> rows = byManager.entrySet().stream()
                .map(entry -> {
                    long paidOrders = entry.getValue().stream().filter(order -> equalsIgnoreCase(order.getStatus(), "PAID")).count();
                    long paidAmount = entry.getValue().stream()
                            .filter(order -> equalsIgnoreCase(order.getStatus(), "PAID"))
                            .map(Order::getTotalAmount)
                            .filter(money -> money != null)
                            .mapToLong(Money::getAmount)
                            .sum();
                    return new ManagerAnalyticsRow(
                            entry.getKey(),
                            entry.getValue().size(),
                            paidOrders,
                            Money.of(paidAmount, "RUB"),
                            Money.of(Math.round(paidAmount * 0.03d), "RUB")
                    );
                })
                .sorted(Comparator.comparing(ManagerAnalyticsRow::paidAmount, Comparator.comparing(Money::getAmount)).reversed())
                .toList();
        return new ManagerAnalyticsResponse(rows);
    }

    public PaymentLinkAnalyticsResponse paymentLinkAnalytics(OffsetDateTime from, OffsetDateTime to, String manager) {
        OffsetDateTime effectiveFrom = from != null ? from : OffsetDateTime.now().minusYears(20);
        OffsetDateTime effectiveTo = to != null ? to : OffsetDateTime.now().plusYears(20);
        String normalizedManager = normalize(manager);
        List<ManagerPaymentLink> links = managerPaymentLinkRepository.findByCreatedAtBetweenOrderByCreatedAtDesc(effectiveFrom, effectiveTo);
        List<PaymentLinkAnalyticsRow> rows = links.stream()
                .filter(link -> !StringUtils.hasText(normalizedManager) || matchesManager(link, normalizedManager))
                .map(link -> {
                    Order order = orderRepository.findById(link.getOrderId()).orElse(null);
                    boolean paid = order != null && equalsIgnoreCase(order.getStatus(), "PAID");
                    return new PaymentLinkAnalyticsRow(
                            link.getId(),
                            link.getOrderId(),
                            link.getManagerSubject(),
                            link.getManagerEmail(),
                            link.getStatus(),
                            paid,
                            link.getSentAt(),
                            link.getCreatedAt()
                    );
                })
                .toList();
        long sent = rows.size();
        long paid = rows.stream().filter(PaymentLinkAnalyticsRow::paid).count();
        double conversion = sent > 0 ? (double) paid / sent : 0d;
        return new PaymentLinkAnalyticsResponse(sent, paid, conversion, rows);
    }

    private Set<UUID> managerLinkedOrderIds(String normalizedManager) {
        if (!StringUtils.hasText(normalizedManager)) {
            return Set.of();
        }
        return managerPaymentLinkRepository.findAll().stream()
                .filter(link -> matchesManager(link, normalizedManager))
                .map(ManagerPaymentLink::getOrderId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
    }

    private boolean matchesManager(ManagerPaymentLink link, String normalizedManager) {
        return containsIgnoreCase(link.getManagerSubject(), normalizedManager)
                || containsIgnoreCase(link.getManagerEmail(), normalizedManager);
    }

    public LowStockAlertResponse lowStockAlerts() {
        int threshold = stockThreshold();
        List<LowStockRow> rows = productRepository.findAll().stream()
                .flatMap(product -> product.getVariants().stream()
                        .filter(variant -> variant.getStockQuantity() < threshold)
                        .map(variant -> new LowStockRow(
                                variant.getId(),
                                product.getId(),
                                product.getName(),
                                product.getSlug(),
                                variant.getName(),
                                variant.getSku(),
                                variant.getStockQuantity()
                        )))
                .sorted(Comparator.comparingInt(LowStockRow::stock))
                .toList();
        return new LowStockAlertResponse(threshold, rows);
    }

    public LowStockAlertResponse updateStockAlertSettings(StockAlertSettingsRequest request) {
        StockAlertSettings settings = stockAlertSettingsRepository.findAll().stream().findFirst()
                .orElseGet(StockAlertSettings::new);
        settings.setThreshold(Math.max(0, request.threshold()));
        stockAlertSettingsRepository.save(settings);
        return lowStockAlerts();
    }

    private int stockThreshold() {
        return stockAlertSettingsRepository.findAll().stream()
                .findFirst()
                .map(StockAlertSettings::getThreshold)
                .orElse(5);
    }

    private boolean applyImportRow(CatalogueImportRow row) {
        ProductVariant existingVariant = variantRepository.findBySku(row.getSku()).orElse(null);
        if (existingVariant == null) {
            return false;
        }
        int delta = row.getStockQuantity() - existingVariant.getStockQuantity();
        if (delta != 0) {
            inventoryService.adjustStock(existingVariant.getId(), delta, "stock-import-" + row.getId(), "STOCK_IMPORT");
            return true;
        }
        return false;
    }

    private ImportReport buildImportReport(List<CatalogueImportRow> rows) {
        List<CatalogueImportRow> safeRows = rows != null ? rows : List.of();
        Map<String, CatalogueImportRow> validRowsBySku = safeRows.stream()
                .filter(CatalogueImportRow::isValid)
                .filter(row -> StringUtils.hasText(row.getSku()))
                .collect(Collectors.toMap(
                        row -> normalizeSku(row.getSku()),
                        Function.identity(),
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
        List<ProductVariant> variants = variantRepository.findAllByOrderBySkuAsc();
        Map<String, ProductVariant> variantsBySku = variants.stream()
                .filter(variant -> StringUtils.hasText(variant.getSku()))
                .collect(Collectors.toMap(
                        variant -> normalizeSku(variant.getSku()),
                        Function.identity(),
                        (left, right) -> left,
                        LinkedHashMap::new
                ));

        int matched = 0;
        int skipped = 0;
        int changed = 0;
        int unchanged = 0;
        for (CatalogueImportRow row : safeRows) {
            if (!row.isValid() || !StringUtils.hasText(row.getSku())) {
                continue;
            }
            ProductVariant variant = variantsBySku.get(normalizeSku(row.getSku()));
            if (variant == null) {
                skipped++;
                continue;
            }
            matched++;
            if (row.getStockQuantity() != null && row.getStockQuantity() == variant.getStockQuantity()) {
                unchanged++;
            } else {
                changed++;
            }
        }

        List<NotUpdatedVariantView> notUpdated = variants.stream()
                .filter(variant -> !validRowsBySku.containsKey(normalizeSku(variant.getSku())))
                .map(this::toNotUpdatedVariantView)
                .toList();

        int invalid = (int) safeRows.stream().filter(row -> !row.isValid()).count();
        return new ImportReport(matched, skipped, changed, unchanged, invalid, notUpdated.size(), notUpdated);
    }

    private int appliedRows(List<CatalogueImportRow> rows) {
        return buildImportReport(rows).changedRows();
    }

    private NotUpdatedVariantView toNotUpdatedVariantView(ProductVariant variant) {
        Product product = variant.getProduct();
        return new NotUpdatedVariantView(
                variant.getId(),
                variant.getSku(),
                product != null ? product.getName() : null,
                product != null ? product.getSlug() : null,
                variant.getName(),
                variant.getStockQuantity(),
                product == null || product.isIsActive()
        );
    }

    private void markDuplicateSkus(List<CatalogueImportRow> rows) {
        Map<String, Long> counts = rows.stream()
                .filter(row -> StringUtils.hasText(row.getSku()))
                .collect(Collectors.groupingBy(row -> normalizeSku(row.getSku()), LinkedHashMap::new, Collectors.counting()));
        for (CatalogueImportRow row : rows) {
            if (!StringUtils.hasText(row.getSku()) || counts.getOrDefault(normalizeSku(row.getSku()), 0L) <= 1L) {
                continue;
            }
            String duplicateMessage = "Duplicate SKU in import file";
            row.setValid(false);
            row.setErrorMessage(StringUtils.hasText(row.getErrorMessage())
                    ? row.getErrorMessage() + "; " + duplicateMessage
                    : duplicateMessage);
        }
    }

    private CatalogueImportRow toImportRow(int rowNumber, Map<String, String> raw, ImportMapping mapping) {
        CatalogueImportRow row = new CatalogueImportRow();
        row.setRowNumber(rowNumber);
        row.setRawData(writeRawData(raw));
        row.setSku(defaultText(read(raw, mapping.sku()), read(raw, SAMPLE_SKU_HEADER)));
        row.setProductName(defaultText(read(raw, mapping.productName()), read(raw, "Номенклатура")));
        row.setProductSlug(read(raw, mapping.productSlug()));
        row.setVariantName(read(raw, mapping.variantName()));
        row.setStockQuantity(parseStockQuantity(raw, mapping));
        validateImportRow(row);
        return row;
    }

    private void validateImportRow(CatalogueImportRow row) {
        List<String> errors = new ArrayList<>();
        if (!StringUtils.hasText(row.getSku())) {
            errors.add("SKU is required");
        }
        if (row.getStockQuantity() == null || row.getStockQuantity() < 0) {
            errors.add("Stock is required");
        }
        row.setValid(errors.isEmpty());
        row.setErrorMessage(errors.isEmpty() ? null : String.join("; ", errors));
    }

    private Integer parseStockQuantity(Map<String, String> raw, ImportMapping mapping) {
        String singleStock = read(raw, mapping.stockQuantity());
        if (StringUtils.hasText(singleStock)) {
            return parseStrictStock(singleStock);
        }
        Integer atrium = parseStrictStock(defaultText(read(raw, ATRIUM_STOCK_HEADER), "0"));
        Integer ip = parseStrictStock(defaultText(read(raw, IP_STOCK_HEADER), "0"));
        if (atrium == null || ip == null) {
            return null;
        }
        return atrium + ip;
    }

    private Integer parseStrictStock(String value) {
        if (!StringUtils.hasText(value)) {
            return 0;
        }
        String normalized = value.trim().replace(" ", "");
        if (!normalized.matches("\\d+")) {
            return null;
        }
        try {
            return Integer.valueOf(normalized);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private List<Map<String, String>> readRows(MultipartFile file, ImportMapping mapping) {
        String name = file.getOriginalFilename() != null ? file.getOriginalFilename().toLowerCase(Locale.ROOT) : "";
        try {
            if (name.endsWith(".xlsx") || name.endsWith(".xls")) {
                return readWorkbookRows(file, mapping);
            }
            return readDelimitedRows(file);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Failed to parse import file: " + ex.getMessage(), ex);
        }
    }

    private int findHeaderRow(Sheet sheet, DataFormatter formatter, ImportMapping mapping) {
        String mappedSku = normalizeHeader(mapping.sku());
        String sampleSku = normalizeHeader(SAMPLE_SKU_HEADER);
        for (int rowIndex = sheet.getFirstRowNum(); rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row == null) {
                continue;
            }
            for (Cell cell : row) {
                String value = normalizeHeader(formatter.formatCellValue(cell));
                if (value.equals(mappedSku) || value.equals(sampleSku)) {
                    return rowIndex;
                }
            }
        }
        return sheet.getFirstRowNum();
    }

    private String formatCellWithMergedHeader(
            Sheet sheet,
            int rowIndex,
            int column,
            DataFormatter formatter,
            Row subheader
    ) {
        Row row = sheet.getRow(rowIndex);
        String direct = row != null ? formatter.formatCellValue(row.getCell(column)).trim() : "";
        if (StringUtils.hasText(direct)) {
            return direct;
        }
        String secondary = subheader != null ? formatter.formatCellValue(subheader.getCell(column)).trim() : "";
        if (!StringUtils.hasText(secondary)) {
            return "";
        }
        for (CellRangeAddress range : sheet.getMergedRegions()) {
            if (range.getFirstRow() == rowIndex
                    && range.getLastRow() == rowIndex
                    && range.getFirstColumn() <= column
                    && range.getLastColumn() >= column) {
                Row firstRow = sheet.getRow(range.getFirstRow());
                return firstRow != null ? formatter.formatCellValue(firstRow.getCell(range.getFirstColumn())).trim() : "";
            }
        }
        return "";
    }

    private String uniqueHeader(List<String> existingHeaders, String headerText, int column) {
        String base = StringUtils.hasText(headerText) ? headerText.trim() : "__column" + (column + 1);
        if (!existingHeaders.contains(base)) {
            return base;
        }
        int suffix = 2;
        String candidate = base + "#" + suffix;
        while (existingHeaders.contains(candidate)) {
            suffix++;
            candidate = base + "#" + suffix;
        }
        return candidate;
    }

    private List<Map<String, String>> readWorkbookRows(MultipartFile file, ImportMapping mapping) throws Exception {
        DataFormatter formatter = new DataFormatter(Locale.ROOT);
        try (var workbook = WorkbookFactory.create(file.getInputStream())) {
            var sheet = workbook.getSheetAt(0);
            if (sheet == null) {
                return List.of();
            }
            int headerRowIndex = findHeaderRow(sheet, formatter, mapping);
            Row header = sheet.getRow(headerRowIndex);
            if (header == null) {
                return List.of();
            }
            Row subheader = sheet.getRow(headerRowIndex + 1);
            int columnCount = Math.max(header.getLastCellNum(), subheader != null ? subheader.getLastCellNum() : 0);
            List<String> headers = new ArrayList<>();
            boolean hasSubheaders = false;
            for (int column = 0; column < columnCount; column++) {
                String primary = formatCellWithMergedHeader(sheet, headerRowIndex, column, formatter, subheader);
                String secondary = subheader != null ? formatter.formatCellValue(subheader.getCell(column)).trim() : "";
                hasSubheaders = hasSubheaders || StringUtils.hasText(secondary);
                String headerText = StringUtils.hasText(primary) && StringUtils.hasText(secondary)
                        ? primary + "." + secondary
                        : defaultText(primary, secondary);
                headers.add(uniqueHeader(headers, headerText, column));
            }
            List<Map<String, String>> rows = new ArrayList<>();
            int firstDataRow = headerRowIndex + (hasSubheaders ? 2 : 1);
            for (int rowIndex = firstDataRow; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row source = sheet.getRow(rowIndex);
                if (source == null) {
                    continue;
                }
                Map<String, String> values = new LinkedHashMap<>();
                values.put("__rowNumber", String.valueOf(rowIndex + 1));
                boolean hasValue = false;
                for (int column = 0; column < headers.size(); column++) {
                    String value = formatter.formatCellValue(source.getCell(column)).trim();
                    values.put(headers.get(column), value);
                    hasValue = hasValue || StringUtils.hasText(value);
                }
                if (hasValue && StringUtils.hasText(defaultText(read(values, mapping.sku()), read(values, SAMPLE_SKU_HEADER)))) {
                    rows.add(values);
                }
            }
            return rows;
        }
    }

    private List<Map<String, String>> readDelimitedRows(MultipartFile file) throws Exception {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String headerLine = reader.readLine();
            if (headerLine == null) {
                return List.of();
            }
            String delimiter = headerLine.contains(";") ? ";" : ",";
            List<String> headers = splitDelimited(headerLine, delimiter);
            List<Map<String, String>> rows = new ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null) {
                List<String> values = splitDelimited(line, delimiter);
                Map<String, String> row = new LinkedHashMap<>();
                row.put("__rowNumber", String.valueOf(rows.size() + 2));
                boolean hasValue = false;
                for (int i = 0; i < headers.size(); i++) {
                    String value = i < values.size() ? values.get(i).trim() : "";
                    row.put(headers.get(i), value);
                    hasValue = hasValue || StringUtils.hasText(value);
                }
                if (hasValue) {
                    rows.add(row);
                }
            }
            return rows;
        }
    }

    private List<String> splitDelimited(String line, String delimiter) {
        return List.of(line.split(java.util.regex.Pattern.quote(delimiter), -1));
    }

    private ImportMapping resolveMapping(ImportMapping mapping) {
        if (mapping == null) {
            mapping = new ImportMapping(null, null, null, null, null, null, null, null, null);
        }
        return new ImportMapping(
                defaultText(mapping.sku(), SAMPLE_SKU_HEADER),
                defaultText(mapping.productName(), "Номенклатура"),
                defaultText(mapping.productSlug(), "product_slug"),
                defaultText(mapping.variantName(), "variant_name"),
                defaultText(mapping.brandSlug(), "brand_slug"),
                defaultText(mapping.categorySlug(), "category_slug"),
                defaultText(mapping.priceAmount(), "price"),
                defaultText(mapping.stockQuantity(), "stock"),
                defaultText(mapping.currency(), "currency")
        );
    }

    private ImportRowView toImportRowView(CatalogueImportRow row) {
        return new ImportRowView(
                row.getId(),
                row.getRowNumber(),
                row.getSku(),
                row.getProductName(),
                row.getProductSlug(),
                row.getVariantName(),
                row.getBrandSlug(),
                row.getCategorySlug(),
                row.getPriceAmount(),
                row.getCurrency(),
                row.getStockQuantity(),
                row.isValid(),
                row.getErrorMessage(),
                readRawData(row.getRawData())
        );
    }

    private PromotionTarget toPromotionTarget(PromotionTargetRequest request) {
        PromotionTarget target = new PromotionTarget();
        target.setTargetKind(request.targetKind().trim().toUpperCase(Locale.ROOT));
        target.setTargetKey(request.targetKey().trim().toLowerCase(Locale.ROOT));
        return target;
    }

    private PromotionView toPromotionView(Promotion promotion) {
        return new PromotionView(
                promotion.getId(),
                promotion.getName(),
                promotion.getType(),
                promotion.getStatus(),
                promotion.getStartsAt(),
                promotion.getEndsAt(),
                promotion.getDiscountPercent(),
                promotion.getDiscountAmount(),
                promotion.getSalePriceAmount(),
                promotion.getCurrency(),
                promotion.getThresholdAmount(),
                promotion.getDescription(),
                promotion.getTargets().stream()
                        .map(target -> new PromotionTargetView(target.getId(), target.getTargetKind(), target.getTargetKey()))
                        .toList(),
                isActiveNow(promotion)
        );
    }

    private PromoCodeView toPromoCodeView(PromoCode promoCode) {
        return new PromoCodeView(
                promoCode.getId(),
                promoCode.getCode(),
                promoCode.getStatus(),
                promoCode.getDiscountPercent(),
                promoCode.getDiscountAmount(),
                promoCode.getThresholdAmount(),
                promoCode.getStartsAt(),
                promoCode.getEndsAt(),
                promoCode.getMaxRedemptions(),
                promoCode.getRedemptionCount(),
                promoCode.getDescription(),
                isActiveNow(promoCode.getStatus(), promoCode.getStartsAt(), promoCode.getEndsAt())
        );
    }

    private TaxConfigurationView toTaxConfigurationView(TaxConfiguration config) {
        return new TaxConfigurationView(
                config.getId(),
                config.getName(),
                config.getStatus(),
                config.getTaxSystemCode(),
                config.getVatCode(),
                config.getVatRatePercent(),
                config.isActive(),
                config.getUpdatedAt()
        );
    }

    private boolean isActiveNow(Promotion promotion) {
        return isActiveNow(promotion.getStatus(), promotion.getStartsAt(), promotion.getEndsAt());
    }

    private boolean isActiveNow(String status, OffsetDateTime startsAt, OffsetDateTime endsAt) {
        OffsetDateTime now = OffsetDateTime.now();
        return "ACTIVE".equalsIgnoreCase(status)
                && (startsAt == null || !startsAt.isAfter(now))
                && (endsAt == null || !endsAt.isBefore(now));
    }

    private Long parseMoneyMinor(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim().replace(" ", "").replace(',', '.');
        try {
            BigDecimal decimal = new BigDecimal(normalized);
            return decimal.movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact();
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private Integer parseInteger(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return new BigDecimal(value.trim().replace(" ", "").replace(',', '.')).setScale(0, RoundingMode.DOWN).intValueExact();
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private String read(Map<String, String> raw, String key) {
        if (raw == null || !StringUtils.hasText(key)) {
            return null;
        }
        String direct = raw.get(key);
        if (StringUtils.hasText(direct)) {
            return direct.trim();
        }
        String wanted = normalizeHeader(key);
        for (Map.Entry<String, String> entry : raw.entrySet()) {
            if (normalizeHeader(entry.getKey()).equals(wanted)) {
                return normalize(entry.getValue());
            }
        }
        return null;
    }

    private String normalizeHeader(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", "");
    }

    private String writeRawData(Map<String, String> raw) {
        try {
            return objectMapper.writeValueAsString(raw);
        } catch (Exception ex) {
            return "{}";
        }
    }

    private Map<String, String> readRawData(String raw) {
        if (!StringUtils.hasText(raw)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(raw, STRING_MAP);
        } catch (Exception ex) {
            return Map.of();
        }
    }

    private Long nonNegative(Long value) {
        return value != null && value >= 0 ? value : null;
    }

    private String slugify(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String slug = value.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        return StringUtils.hasText(slug) ? slug : value.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", "-");
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String normalizeSku(String value) {
        return StringUtils.hasText(value) ? value.trim().toLowerCase(Locale.ROOT) : "";
    }

    private String textCell(String value) {
        return StringUtils.hasText(value) ? value.trim() : "";
    }

    private String defaultText(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }

    private String requireText(String value, String field) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.trim();
    }

    private boolean equalsIgnoreCase(String left, String right) {
        return left != null && right != null && left.equalsIgnoreCase(right);
    }

    private boolean containsIgnoreCase(String value, String query) {
        return StringUtils.hasText(value)
                && StringUtils.hasText(query)
                && value.toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT));
    }
}
