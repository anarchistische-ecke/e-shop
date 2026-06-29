package com.example.api.admincms;

import com.example.api.catalog.DirectusBridgeSecurity;
import com.example.catalog.domain.Product;
import com.example.catalog.domain.ProductVariant;
import com.example.catalog.repository.ProductRepository;
import com.example.catalog.repository.ProductVariantRepository;
import com.example.catalog.service.CatalogService;
import com.example.catalog.service.InventoryService;
import com.example.common.domain.Money;
import com.example.order.repository.OrderRepository;
import com.example.order.service.OrderService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DirectusAdminServiceStockImportTest {

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
    void dryRun_parsesSampleWorkbookAndReportsMatchedSkippedChangedAndNotUpdated() throws Exception {
        UUID jobId = UUID.randomUUID();
        ProductVariant changed = variant("SKU-1", "Variant one", 2, product("Product one", "product-one", true));
        ProductVariant unchanged = variant("SKU-2", "Variant two", 5, product("Product two", "product-two", true));
        ProductVariant inactiveNotUpdated = variant("SKU-3", "Variant three", 7, product("Product three", "product-three", false));
        MockMultipartFile file = sampleWorkbook(
                row("SKU-1", "Product one", null, 4),
                row("SKU-2", "Product two", 5, null),
                row("UNKNOWN", "Unknown", 1, 1)
        );
        stubDryRunPersistence(jobId);
        when(variantRepository.findAllByOrderBySkuAsc()).thenReturn(List.of(changed, inactiveNotUpdated, unchanged));

        var response = service.dryRunImport(file, null, principal());

        assertThat(response.job().totalRows()).isEqualTo(3);
        assertThat(response.job().validRows()).isEqualTo(3);
        assertThat(response.job().invalidRows()).isZero();
        assertThat(response.report().matchedRows()).isEqualTo(2);
        assertThat(response.report().skippedRows()).isEqualTo(1);
        assertThat(response.report().changedRows()).isEqualTo(1);
        assertThat(response.report().unchangedRows()).isEqualTo(1);
        assertThat(response.report().notUpdatedVariants())
                .extracting(DirectusAdminModels.NotUpdatedVariantView::sku)
                .containsExactly("SKU-3");
        assertThat(response.rows())
                .extracting(DirectusAdminModels.ImportRowView::stockQuantity)
                .containsExactly(4, 5, 2);
    }

    @Test
    void dryRun_marksDuplicateSkuInvalidAndCommitBlocksInvalidJob() throws Exception {
        UUID jobId = UUID.randomUUID();
        MockMultipartFile file = sampleWorkbook(
                row("SKU-1", "Product one", 1, 0),
                row("SKU-1", "Product one duplicate", 2, 0)
        );
        stubDryRunPersistence(jobId);
        when(variantRepository.findAllByOrderBySkuAsc()).thenReturn(List.of(variant("SKU-1", "Variant one", 2, product("Product one", "product-one", true))));

        var response = service.dryRunImport(file, null, principal());

        assertThat(response.job().invalidRows()).isEqualTo(2);
        assertThat(response.rows()).allMatch(row -> row.errorMessage().contains("Duplicate SKU"));

        CatalogueImportJob job = new CatalogueImportJob();
        job.setId(jobId);
        job.setStatus("DRY_RUN");
        job.setInvalidRows(2);
        when(importJobRepository.findById(jobId)).thenReturn(Optional.of(job));

        assertThatThrownBy(() -> service.commitImport(jobId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("invalid rows");
    }

    @Test
    void dryRun_rejectsDecimalNegativeAndNonNumericStock() throws Exception {
        UUID jobId = UUID.randomUUID();
        MockMultipartFile file = sampleWorkbookWithRawStock(
                rawRow("SKU-1", "Decimal", "1.5", "0"),
                rawRow("SKU-2", "Negative", "-1", "0"),
                rawRow("SKU-3", "Text", "many", "0")
        );
        stubDryRunPersistence(jobId);
        when(variantRepository.findAllByOrderBySkuAsc()).thenReturn(List.of());

        var response = service.dryRunImport(file, null, principal());

        assertThat(response.job().invalidRows()).isEqualTo(3);
        assertThat(response.rows()).allMatch(row -> row.errorMessage().contains("Stock is required"));
    }

    @Test
    void commit_updatesOnlyChangedMatchedStocksAndDoesNotMutateCatalogueMetadata() throws Exception {
        UUID jobId = UUID.randomUUID();
        Product product = product("Product one", "product-one", true);
        ProductVariant changed = variant("SKU-1", "Variant one", 2, product);
        ProductVariant unchanged = variant("SKU-2", "Variant two", 5, product("Product two", "product-two", true));
        ProductVariant notUpdated = variant("SKU-3", "Variant three", 9, product("Product three", "product-three", true));
        List<CatalogueImportRow> rows = List.of(
                savedRow(jobId, "SKU-1", 6, true, null),
                savedRow(jobId, "SKU-2", 5, true, null),
                savedRow(jobId, "UNKNOWN", 2, true, null)
        );
        CatalogueImportJob job = new CatalogueImportJob();
        job.setId(jobId);
        job.setStatus("DRY_RUN");
        job.setInvalidRows(0);

        when(importJobRepository.findById(jobId)).thenReturn(Optional.of(job));
        when(importRowRepository.findByJobIdOrderByRowNumberAsc(jobId)).thenReturn(rows);
        when(variantRepository.findAllByOrderBySkuAsc()).thenReturn(List.of(changed, unchanged, notUpdated));
        when(variantRepository.findBySku("SKU-1")).thenReturn(Optional.of(changed));
        when(variantRepository.findBySku("SKU-2")).thenReturn(Optional.of(unchanged));
        when(variantRepository.findBySku("UNKNOWN")).thenReturn(Optional.empty());
        when(importJobRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.commitImport(jobId);

        assertThat(response.appliedRows()).isEqualTo(1);
        assertThat(response.report().matchedRows()).isEqualTo(2);
        assertThat(response.report().skippedRows()).isEqualTo(1);
        assertThat(response.report().changedRows()).isEqualTo(1);
        assertThat(response.report().unchangedRows()).isEqualTo(1);
        verify(inventoryService).adjustStock(eq(changed.getId()), eq(4), eq("stock-import-" + rows.getFirst().getId()), eq("STOCK_IMPORT"));
        verify(catalogService, never()).createProduct(any(), any(), any());
        verify(catalogService, never()).updateProduct(any(), any(), any(Boolean.class), any());
        verify(catalogService, never()).addVariant(any(), any(), any(), any(), any(Integer.class), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
        verify(catalogService, never()).updateVariant(any(), any(), any(), any(), any(Integer.class), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void notUpdatedExportsContainExpectedRows() {
        UUID jobId = UUID.randomUUID();
        ProductVariant notUpdated = variant("SKU-9", "Variant nine", 11, product("Product nine", "product-nine", false));
        when(importJobRepository.existsById(jobId)).thenReturn(true);
        when(importRowRepository.findByJobIdOrderByRowNumberAsc(jobId)).thenReturn(List.of(savedRow(jobId, "SKU-1", 1, true, null)));
        when(variantRepository.findAllByOrderBySkuAsc()).thenReturn(List.of(notUpdated));

        String text = new String(service.notUpdatedImportReportText(jobId));
        byte[] workbook = service.notUpdatedImportReportWorkbook(jobId);

        assertThat(text).contains("SKU-9").contains("Product nine").contains("false");
        assertThat(workbook).isNotEmpty();
    }

    private List<CatalogueImportRow> stubDryRunPersistence(UUID jobId) {
        List<CatalogueImportRow> savedRows = new ArrayList<>();
        when(importJobRepository.save(any())).thenAnswer(invocation -> {
            CatalogueImportJob job = invocation.getArgument(0);
            job.setId(jobId);
            return job;
        });
        when(importRowRepository.saveAll(any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            List<CatalogueImportRow> rows = invocation.getArgument(0);
            for (CatalogueImportRow row : rows) {
                row.setId(UUID.randomUUID());
                savedRows.add(row);
            }
            return rows;
        });
        return savedRows;
    }

    private CatalogueImportRow savedRow(UUID jobId, String sku, int stock, boolean valid, String error) {
        CatalogueImportRow row = new CatalogueImportRow();
        row.setId(UUID.randomUUID());
        row.setJobId(jobId);
        row.setRowNumber(1);
        row.setSku(sku);
        row.setStockQuantity(stock);
        row.setValid(valid);
        row.setErrorMessage(error);
        return row;
    }

    private Product product(String name, String slug, boolean active) {
        Product product = new Product(name, null, slug);
        product.setId(UUID.randomUUID());
        product.setIsActive(active);
        return product;
    }

    private ProductVariant variant(String sku, String name, int stock, Product product) {
        ProductVariant variant = new ProductVariant(sku, name, Money.of(100, "RUB"), stock);
        variant.setId(UUID.randomUUID());
        variant.setProduct(product);
        return variant;
    }

    private DirectusBridgeSecurity.DirectusBridgePrincipal principal() {
        return new DirectusBridgeSecurity.DirectusBridgePrincipal("user-id", "user@example.test", "external-id", "content-role", "content-role");
    }

    private StockRow row(String sku, String name, Integer atrium, Integer ip) {
        return new StockRow(sku, name, atrium, ip);
    }

    private MockMultipartFile sampleWorkbook(StockRow... rows) throws Exception {
        List<RawStockRow> rawRows = new ArrayList<>();
        for (StockRow row : rows) {
            rawRows.add(rawRow(
                    row.sku(),
                    row.name(),
                    row.atrium() != null ? String.valueOf(row.atrium()) : null,
                    row.ip() != null ? String.valueOf(row.ip()) : null
            ));
        }
        return sampleWorkbookWithRawStock(rawRows.toArray(new RawStockRow[0]));
    }

    private RawStockRow rawRow(String sku, String name, String atrium, String ip) {
        return new RawStockRow(sku, name, atrium, ip);
    }

    private MockMultipartFile sampleWorkbookWithRawStock(RawStockRow... rows) throws Exception {
        try (var workbook = new XSSFWorkbook(); var out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Лист_1");
            sheet.createRow(0).createCell(0).setCellValue("Прайс-лист");
            Row header = sheet.createRow(2);
            Row subheader = sheet.createRow(3);
            header.createCell(4).setCellValue("Номенклатура.Артикул");
            header.createCell(12).setCellValue("Номенклатура");
            header.createCell(18).setCellValue("Склад 21 Век АТРИУМ");
            header.createCell(20).setCellValue("Склад 21 Век ИП");
            subheader.createCell(18).setCellValue("Остаток");
            subheader.createCell(19).setCellValue("Наличие");
            subheader.createCell(20).setCellValue("Остаток");
            subheader.createCell(21).setCellValue("Наличие");
            sheet.addMergedRegion(new CellRangeAddress(2, 3, 4, 11));
            sheet.addMergedRegion(new CellRangeAddress(2, 3, 12, 13));
            sheet.addMergedRegion(new CellRangeAddress(2, 2, 18, 19));
            sheet.addMergedRegion(new CellRangeAddress(2, 2, 20, 21));
            int rowIndex = 4;
            Row category = sheet.createRow(rowIndex++);
            category.createCell(12).setCellValue("Category row");
            for (RawStockRow stockRow : rows) {
                Row row = sheet.createRow(rowIndex++);
                row.createCell(4).setCellValue(stockRow.sku());
                row.createCell(12).setCellValue(stockRow.name());
                if (stockRow.atrium() != null) {
                    row.createCell(18).setCellValue(stockRow.atrium());
                }
                if (stockRow.ip() != null) {
                    row.createCell(20).setCellValue(stockRow.ip());
                }
            }
            workbook.write(out);
            return new MockMultipartFile("file", "stock.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", out.toByteArray());
        }
    }

    private record StockRow(String sku, String name, Integer atrium, Integer ip) {
    }

    private record RawStockRow(String sku, String name, String atrium, String ip) {
    }
}
