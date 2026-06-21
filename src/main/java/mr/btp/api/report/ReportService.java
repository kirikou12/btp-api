package mr.btp.api.report;

import mr.btp.api.common.service.ReferenceDataService;
import mr.btp.api.invoice.InvoiceType;
import mr.btp.api.invoice.SupplierInvoice;
import mr.btp.api.invoice.SupplierInvoiceItem;
import mr.btp.api.invoice.SupplierInvoiceItemRepository;
import mr.btp.api.invoice.SupplierInvoiceRepository;
import mr.btp.api.worker.WorkerPayment;
import mr.btp.api.worker.WorkerPaymentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ReportService {

    private static final List<InvoiceType> USAGE_INVOICE_TYPES = List.of(InvoiceType.SUPPLY_USAGE, InvoiceType.DIRECT_USAGE, InvoiceType.DIRECT_EXPENSE);

    private final ReferenceDataService referenceDataService;
    private final SupplierInvoiceItemRepository invoiceItemRepository;
    private final SupplierInvoiceRepository invoiceRepository;
    private final WorkerPaymentRepository workerPaymentRepository;

    public ReportService(ReferenceDataService referenceDataService,
                         SupplierInvoiceItemRepository invoiceItemRepository,
                         SupplierInvoiceRepository invoiceRepository,
                         WorkerPaymentRepository workerPaymentRepository) {
        this.referenceDataService = referenceDataService;
        this.invoiceItemRepository = invoiceItemRepository;
        this.invoiceRepository = invoiceRepository;
        this.workerPaymentRepository = workerPaymentRepository;
    }

    @Transactional(readOnly = true)
    public List<ReportDtos.StageCostRow> stageCosts(Long projectId) {
        Map<Long, BigDecimal> totals = new LinkedHashMap<>();
        referenceDataService.stagesByProject(projectId).forEach(stage -> totals.put(stage.getId(), BigDecimal.ZERO));
        invoiceItemRepository.sumUsageTotalsByStage(projectId, usageInvoiceTypes()).forEach(row ->
                totals.computeIfPresent(row.getStageId(), (key, value) -> value.add(row.getTotalAmount())));
        workerPaymentRepository.sumAmountsByStageForProject(projectId).forEach(row ->
                totals.computeIfPresent(row.getStageId(), (key, value) -> value.add(row.getTotalAmount())));
        return referenceDataService.stagesByProject(projectId).stream()
                .map(stage -> new ReportDtos.StageCostRow(stage.getId(), stage.getName(), totals.getOrDefault(stage.getId(), BigDecimal.ZERO)))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ReportDtos.CategoryCostRow> categoryCosts(Long projectId) {
        Map<Long, ReportDtos.CategoryCostRow> rows = new LinkedHashMap<>();
        invoiceItemRepository.sumUsageTotalsByCategory(projectId, usageInvoiceTypes()).forEach(row -> rows.merge(
                row.getCategoryId(),
                new ReportDtos.CategoryCostRow(row.getCategoryId(), row.getCategoryName(), row.getTotalAmount()),
                (left, right) -> new ReportDtos.CategoryCostRow(left.categoryId(), left.categoryName(), left.totalCost().add(right.totalCost()))
        ));
        workerPaymentRepository.sumAmountsByWorkerTypeForProject(projectId).forEach(row -> {
            Long workerCategoryKey = -1L - row.getWorkerType().ordinal();
            String workerCategoryName = "Workers - " + row.getWorkerType().name();
            rows.merge(
                    workerCategoryKey,
                    new ReportDtos.CategoryCostRow(workerCategoryKey, workerCategoryName, row.getTotalAmount()),
                    (left, right) -> new ReportDtos.CategoryCostRow(left.categoryId(), left.categoryName(), left.totalCost().add(right.totalCost()))
            );
        });
        return new ArrayList<>(rows.values());
    }

    @Transactional(readOnly = true)
    public List<ReportDtos.SupplierBalanceRow> supplierBalances(Long projectId) {
        Map<Long, ReportDtos.SupplierBalanceRow> rows = new LinkedHashMap<>();
        List<SupplierInvoice> invoices = referenceDataService.invoicesByProject(projectId);
        Map<Long, List<SupplierInvoiceItem>> itemsByInvoiceId = supplyItemsByInvoice(invoices);
        List<Long> sourceItemIds = itemsByInvoiceId.values().stream()
                .flatMap(List::stream)
                .map(SupplierInvoiceItem::getId)
                .toList();
        Map<Long, BigDecimal> consumedBySourceItem = outgoingAmountBySourceItem(sourceItemIds, List.of(InvoiceType.SUPPLY_USAGE));
        Map<Long, BigDecimal> outgoingBySourceItem = outgoingAmountBySourceItem(sourceItemIds, List.of(InvoiceType.SUPPLY_USAGE, InvoiceType.SUPPLY_RETURN));
        for (SupplierInvoice invoice : invoices) {
            List<SupplierInvoiceItem> invoiceItems = itemsByInvoiceId.getOrDefault(invoice.getId(), List.of());
            BigDecimal consumed = invoiceItems.stream()
                    .map(item -> consumedBySourceItem.getOrDefault(item.getId(), BigDecimal.ZERO))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal outgoing = invoiceItems.stream()
                    .map(item -> outgoingBySourceItem.getOrDefault(item.getId(), BigDecimal.ZERO))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal exchanged = invoiceRepository.sumExchangeTotalBySourceInvoiceId(invoice.getId(), null);
            rows.merge(
                    invoice.getSupplier().getId(),
                    new ReportDtos.SupplierBalanceRow(invoice.getSupplier().getId(), invoice.getSupplier().getName(), invoice.getTotalAmount(), consumed, invoice.getTotalAmount().subtract(outgoing).subtract(exchanged)),
                    (left, right) -> new ReportDtos.SupplierBalanceRow(
                            left.supplierId(),
                            left.supplierName(),
                            left.totalInvoiced().add(right.totalInvoiced()),
                            left.totalConsumed().add(right.totalConsumed()),
                            left.remainingBalance().add(right.remainingBalance())
                    )
            );
        }
        return new ArrayList<>(rows.values());
    }

    private Map<Long, List<SupplierInvoiceItem>> supplyItemsByInvoice(List<SupplierInvoice> invoices) {
        List<Long> invoiceIds = invoices.stream().map(SupplierInvoice::getId).toList();
        if (invoiceIds.isEmpty()) {
            return Map.of();
        }
        return invoiceItemRepository.findDetailedByInvoiceIdIn(invoiceIds).stream()
                .collect(java.util.stream.Collectors.groupingBy(item -> item.getInvoice().getId()));
    }

    private Map<Long, BigDecimal> outgoingAmountBySourceItem(List<Long> sourceItemIds, List<InvoiceType> invoiceTypes) {
        if (sourceItemIds.isEmpty()) {
            return Map.of();
        }
        return invoiceItemRepository.sumOutgoingBySourceItemIds(sourceItemIds, invoiceTypes, null).stream()
                .collect(java.util.stream.Collectors.toMap(
                        SupplierInvoiceItemRepository.SourceItemUsageTotal::getSourceSupplyItemId,
                        SupplierInvoiceItemRepository.SourceItemUsageTotal::getTotalAmount
                ));
    }

    private List<InvoiceType> usageInvoiceTypes() {
        return USAGE_INVOICE_TYPES;
    }

    @Transactional(readOnly = true)
    public List<ReportDtos.ActivityFeedRow> activityFeed(Long projectId) {
        List<ReportDtos.ActivityFeedRow> rows = new ArrayList<>();
        referenceDataService.usageInvoicesByProject(projectId).forEach(invoice -> rows.add(
                new ReportDtos.ActivityFeedRow(activityType(invoice), invoice.getReference() == null ? activityTitle(invoice) : invoice.getReference(), invoice.getTotalAmount(), invoice.getInvoiceDate(), usageInvoiceDetails(invoice))
        ));
        referenceDataService.workerPaymentsByProject(projectId).forEach(payment -> rows.add(
                new ReportDtos.ActivityFeedRow("WORKER_PAYMENT", payment.getWorker().getName(), payment.getAmount(), payment.getPaymentDate(), workerPaymentDetails(payment))
        ));
        referenceDataService.invoicesByProject(projectId).forEach(invoice -> rows.add(
                new ReportDtos.ActivityFeedRow("INVOICE", invoice.getReference() == null ? "Supplier invoice" : invoice.getReference(), invoice.getTotalAmount(), invoice.getInvoiceDate(), invoice.getSupplier().getName())
        ));
        rows.sort(Comparator.comparing(ReportDtos.ActivityFeedRow::activityDate).reversed());
        return rows;
    }

    private String workerPaymentDetails(WorkerPayment payment) {
        return payment.getStage().getName() + " • " + payment.getWorker().getType().name();
    }

    private String usageInvoiceDetails(SupplierInvoice invoice) {
        String stageName = invoice.getStage() == null ? "No stage" : invoice.getStage().getName();
        String supplierName = invoice.getSupplier() == null ? "No supplier" : invoice.getSupplier().getName();
        return stageName + " • " + supplierName;
    }

    private String activityType(SupplierInvoice invoice) {
        if (invoice.getInvoiceType() == InvoiceType.DIRECT_EXPENSE) {
            return "DIRECT_EXPENSE";
        }
        if (invoice.getInvoiceType() == InvoiceType.DIRECT_USAGE) {
            return "DIRECT_USAGE";
        }
        return "USAGE_INVOICE";
    }

    private String activityTitle(SupplierInvoice invoice) {
        if (invoice.getInvoiceType() == InvoiceType.DIRECT_EXPENSE) {
            return "Direct expense invoice";
        }
        if (invoice.getInvoiceType() == InvoiceType.DIRECT_USAGE) {
            return "Direct usage invoice";
        }
        return "Usage invoice";
    }
}
