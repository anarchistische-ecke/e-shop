package com.example.api.admincms;

import com.example.api.admincms.DirectusAdminModels.ImportCommitResponse;
import com.example.api.admincms.DirectusAdminModels.ImportDryRunResponse;
import com.example.api.admincms.DirectusAdminModels.ImportJobView;
import com.example.api.admincms.DirectusAdminModels.ImportMapping;
import com.example.api.admincms.DirectusAdminModels.ImportRowView;
import com.example.api.admincms.DirectusAdminModels.LowStockAlertResponse;
import com.example.api.admincms.DirectusAdminModels.LowStockRow;
import com.example.api.admincms.DirectusAdminModels.ManagerAnalyticsResponse;
import com.example.api.admincms.DirectusAdminModels.ManagerAnalyticsRow;
import com.example.api.admincms.DirectusAdminModels.OrderDetail;
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
import com.example.api.admincms.DirectusAdminModels.StockAlertSettingsRequest;
import com.example.api.admincms.DirectusAdminModels.TaxConfigurationRequest;
import com.example.api.admincms.DirectusAdminModels.TaxConfigurationView;
import com.example.api.catalog.DirectusBridgeSecurity;
import com.example.catalog.domain.Brand;
import com.example.catalog.domain.Category;
import com.example.catalog.domain.Product;
import com.example.catalog.domain.ProductVariant;
import com.example.catalog.repository.ProductRepository;
import com.example.catalog.repository.ProductVariantRepository;
import com.example.catalog.service.CatalogService;
import com.example.catalog.service.InventoryService;
import com.example.common.domain.Money;
import com.example.order.domain.Order;
import com.example.order.repository.OrderRepository;
import com.example.order.service.OrderService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.transaction.Transactional;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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

    private final OrderRepository orderRepository;
    private final OrderService orderService;
    private final OrderStatusHistoryRepository orderStatusHistoryRepository;
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

    public DirectusAdminService(
            OrderRepository orderRepository,
            OrderService orderService,
            OrderStatusHistoryRepository orderStatusHistoryRepository,
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
        this.orderRepository = orderRepository;
        this.orderService = orderService;
        this.orderStatusHistoryRepository = orderStatusHistoryRepository;
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
    }

    public OrderSearchResponse searchOrders(String status, String manager, OffsetDateTime from, OffsetDateTime to, String query) {
        String normalizedStatus = normalize(status);
        String normalizedManager = normalize(manager);
        String normalizedQuery = normalize(query);
        List<DirectusAdminModels.OrderSummary> items = orderRepository.findAllByOrderByOrderDateDesc().stream()
                .filter(order -> !StringUtils.hasText(normalizedStatus) || equalsIgnoreCase(order.getStatus(), normalizedStatus))
                .filter(order -> !StringUtils.hasText(normalizedManager) || containsIgnoreCase(order.getManagerSubject(), normalizedManager))
                .filter(order -> from == null || (order.getOrderDate() != null && !order.getOrderDate().isBefore(from)))
                .filter(order -> to == null || (order.getOrderDate() != null && !order.getOrderDate().isAfter(to)))
                .filter(order -> !StringUtils.hasText(normalizedQuery)
                        || containsIgnoreCase(String.valueOf(order.getId()), normalizedQuery)
                        || containsIgnoreCase(order.getReceiptEmail(), normalizedQuery)
                        || containsIgnoreCase(order.getPublicToken(), normalizedQuery)
                        || containsIgnoreCase(order.getManagerSubject(), normalizedQuery))
                .map(DirectusAdminModels.OrderSummary::from)
                .toList();
        return new OrderSearchResponse(items);
    }

    public OrderDetail getOrder(UUID orderId) {
        Order order = orderService.findById(orderId);
        return new OrderDetail(
                order,
                orderStatusHistoryRepository.findByOrderIdOrderByCreatedAtAsc(orderId).stream()
                        .map(OrderStatusEvent::from)
                        .toList()
        );
    }

    public OrderDetail updateOrderStatus(UUID orderId,
                                         String status,
                                         String note,
                                         DirectusBridgeSecurity.DirectusBridgePrincipal principal) {
        Order before = orderService.findById(orderId);
        Order updated = orderService.updateOrderStatus(orderId, requireText(status, "status"));
        OrderStatusHistory event = new OrderStatusHistory();
        event.setOrderId(orderId);
        event.setPreviousStatus(before.getStatus());
        event.setNextStatus(updated.getStatus());
        event.setActor(principal.actor());
        event.setActorRole(principal.primaryRole());
        event.setNote(normalize(note));
        orderStatusHistoryRepository.save(event);
        return getOrder(orderId);
    }

    public OrderDetail claimOrder(UUID orderId, DirectusBridgeSecurity.DirectusBridgePrincipal principal) {
        Order order = orderService.findById(orderId);
        OrderStatusHistory event = new OrderStatusHistory();
        event.setOrderId(orderId);
        event.setPreviousStatus(order.getStatus());
        event.setNextStatus(order.getStatus());
        event.setActor(principal.actor());
        event.setActorRole(principal.primaryRole());
        event.setNote("claimed");
        orderStatusHistoryRepository.save(event);
        return getOrder(orderId);
    }

    public ImportDryRunResponse dryRunImport(MultipartFile file, ImportMapping mapping, DirectusBridgeSecurity.DirectusBridgePrincipal principal) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Import file is required");
        }
        ImportMapping resolvedMapping = resolveMapping(mapping);
        List<Map<String, String>> sourceRows = readRows(file);
        CatalogueImportJob job = new CatalogueImportJob();
        job.setFileName(file.getOriginalFilename());
        job.setStatus("DRY_RUN");
        job.setCreatedBy(principal.actor());
        job.setTotalRows(sourceRows.size());

        List<CatalogueImportRow> rows = new ArrayList<>();
        int valid = 0;
        int invalid = 0;
        for (int index = 0; index < sourceRows.size(); index++) {
            CatalogueImportRow row = toImportRow(index + 2, sourceRows.get(index), resolvedMapping);
            if (row.isValid()) {
                valid++;
            } else {
                invalid++;
            }
            rows.add(row);
        }
        job.setValidRows(valid);
        job.setInvalidRows(invalid);
        job = importJobRepository.save(job);
        for (CatalogueImportRow row : rows) {
            row.setJobId(job.getId());
        }
        rows = importRowRepository.saveAll(rows);
        return new ImportDryRunResponse(ImportJobView.from(job), rows.stream().map(this::toImportRowView).toList());
    }

    public ImportCommitResponse commitImport(UUID jobId) {
        CatalogueImportJob job = importJobRepository.findById(jobId)
                .orElseThrow(() -> new IllegalArgumentException("Import job not found: " + jobId));
        if ("COMMITTED".equalsIgnoreCase(job.getStatus())) {
            return new ImportCommitResponse(ImportJobView.from(job), job.getValidRows());
        }
        if (job.getInvalidRows() > 0) {
            throw new IllegalStateException("Import has invalid rows and cannot be committed");
        }

        List<CatalogueImportRow> rows = importRowRepository.findByJobIdOrderByRowNumberAsc(jobId);
        int applied = 0;
        for (CatalogueImportRow row : rows) {
            if (!row.isValid()) {
                continue;
            }
            applyImportRow(row);
            applied++;
        }
        job.setStatus("COMMITTED");
        job.setCommittedAt(OffsetDateTime.now());
        job = importJobRepository.save(job);
        return new ImportCommitResponse(ImportJobView.from(job), applied);
    }

    public List<ImportJobView> listImports() {
        return importJobRepository.findTop25ByOrderByCreatedAtDesc().stream().map(ImportJobView::from).toList();
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
        promotion.setType(defaultText(request.type(), "PRODUCT_SALE").toUpperCase(Locale.ROOT));
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
        promoCode.setMaxRedemptions(request.maxRedemptions());
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
        Map<String, List<Order>> byManager = orderRepository.findAllByOrderByOrderDateDesc().stream()
                .filter(order -> StringUtils.hasText(order.getManagerSubject()))
                .filter(order -> !StringUtils.hasText(normalizedManager) || containsIgnoreCase(order.getManagerSubject(), normalizedManager))
                .filter(order -> from == null || (order.getOrderDate() != null && !order.getOrderDate().isBefore(from)))
                .filter(order -> to == null || (order.getOrderDate() != null && !order.getOrderDate().isAfter(to)))
                .collect(Collectors.groupingBy(Order::getManagerSubject, LinkedHashMap::new, Collectors.toList()));
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
                .filter(link -> !StringUtils.hasText(normalizedManager) || containsIgnoreCase(link.getManagerSubject(), normalizedManager))
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

    private void applyImportRow(CatalogueImportRow row) {
        ProductVariant existingVariant = variantRepository.findBySku(row.getSku()).orElse(null);
        Product product;
        if (existingVariant != null && existingVariant.getProduct() != null) {
            product = existingVariant.getProduct();
        } else {
            product = productRepository.findBySlug(row.getProductSlug())
                    .orElseGet(() -> catalogService.createProduct(row.getProductName(), null, row.getProductSlug()));
        }

        Product updates = new Product(
                StringUtils.hasText(row.getProductName()) ? row.getProductName() : product.getName(),
                product.getDescription(),
                StringUtils.hasText(row.getProductSlug()) ? row.getProductSlug() : product.getSlug()
        );
        if (StringUtils.hasText(row.getBrandSlug())) {
            catalogService.getByBrandSlug(row.getBrandSlug()).ifPresent(updates::setBrand);
        }
        Set<Category> categories = new LinkedHashSet<>();
        if (StringUtils.hasText(row.getCategorySlug())) {
            catalogService.getBySlug(row.getCategorySlug()).ifPresent(categories::add);
        }
        if (!categories.isEmpty()) {
            updates.setCategories(categories);
        }
        product = catalogService.updateProduct(product.getId(), updates, !categories.isEmpty(), true);

        Money price = Money.of(row.getPriceAmount(), defaultText(row.getCurrency(), "RUB"));
        if (existingVariant == null) {
            catalogService.addVariant(product.getId(), row.getSku(), row.getVariantName(), price, row.getStockQuantity(), null, null, null, null);
            return;
        }
        int delta = row.getStockQuantity() - existingVariant.getStockQuantity();
        catalogService.updateVariant(product.getId(), existingVariant.getId(), row.getVariantName(), price, existingVariant.getStockQuantity(), null, null, null, null);
        if (delta != 0) {
            inventoryService.adjustStock(existingVariant.getId(), delta, "catalogue-import-" + row.getId(), "CATALOGUE_IMPORT");
        }
    }

    private CatalogueImportRow toImportRow(int rowNumber, Map<String, String> raw, ImportMapping mapping) {
        CatalogueImportRow row = new CatalogueImportRow();
        row.setRowNumber(rowNumber);
        row.setRawData(writeRawData(raw));
        row.setSku(read(raw, mapping.sku()));
        row.setProductName(read(raw, mapping.productName()));
        row.setProductSlug(defaultText(read(raw, mapping.productSlug()), slugify(defaultText(row.getProductName(), row.getSku()))));
        row.setVariantName(defaultText(read(raw, mapping.variantName()), row.getProductName()));
        row.setBrandSlug(read(raw, mapping.brandSlug()));
        row.setCategorySlug(read(raw, mapping.categorySlug()));
        row.setCurrency(defaultText(read(raw, mapping.currency()), "RUB").toUpperCase(Locale.ROOT));
        row.setPriceAmount(parseMoneyMinor(read(raw, mapping.priceAmount())));
        row.setStockQuantity(parseInteger(read(raw, mapping.stockQuantity())));
        validateImportRow(row);
        return row;
    }

    private void validateImportRow(CatalogueImportRow row) {
        List<String> errors = new ArrayList<>();
        if (!StringUtils.hasText(row.getSku())) {
            errors.add("SKU is required");
        }
        if (!StringUtils.hasText(row.getProductName())) {
            errors.add("Product name is required");
        }
        if (!StringUtils.hasText(row.getProductSlug())) {
            errors.add("Product slug is required");
        }
        if (!StringUtils.hasText(row.getVariantName())) {
            errors.add("Variant name is required");
        }
        if (row.getPriceAmount() == null || row.getPriceAmount() < 0) {
            errors.add("Price is required");
        }
        if (row.getStockQuantity() == null || row.getStockQuantity() < 0) {
            errors.add("Stock is required");
        }
        row.setValid(errors.isEmpty());
        row.setErrorMessage(errors.isEmpty() ? null : String.join("; ", errors));
    }

    private List<Map<String, String>> readRows(MultipartFile file) {
        String name = file.getOriginalFilename() != null ? file.getOriginalFilename().toLowerCase(Locale.ROOT) : "";
        try {
            if (name.endsWith(".xlsx") || name.endsWith(".xls")) {
                return readWorkbookRows(file);
            }
            return readDelimitedRows(file);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Failed to parse import file: " + ex.getMessage(), ex);
        }
    }

    private List<Map<String, String>> readWorkbookRows(MultipartFile file) throws Exception {
        DataFormatter formatter = new DataFormatter(Locale.ROOT);
        try (var workbook = WorkbookFactory.create(file.getInputStream())) {
            var sheet = workbook.getSheetAt(0);
            if (sheet == null) {
                return List.of();
            }
            Row header = sheet.getRow(sheet.getFirstRowNum());
            if (header == null) {
                return List.of();
            }
            List<String> headers = new ArrayList<>();
            for (Cell cell : header) {
                headers.add(formatter.formatCellValue(cell).trim());
            }
            List<Map<String, String>> rows = new ArrayList<>();
            for (int rowIndex = sheet.getFirstRowNum() + 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row source = sheet.getRow(rowIndex);
                if (source == null) {
                    continue;
                }
                Map<String, String> values = new LinkedHashMap<>();
                boolean hasValue = false;
                for (int column = 0; column < headers.size(); column++) {
                    String value = formatter.formatCellValue(source.getCell(column)).trim();
                    values.put(headers.get(column), value);
                    hasValue = hasValue || StringUtils.hasText(value);
                }
                if (hasValue) {
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
                defaultText(mapping.sku(), "sku"),
                defaultText(mapping.productName(), "product_name"),
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
