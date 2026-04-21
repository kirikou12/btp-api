package mr.btp.api.report;

import mr.btp.api.common.service.ReferenceDataService;
import mr.btp.api.invoice.InvoiceType;
import mr.btp.api.invoice.SupplierInvoice;
import mr.btp.api.worker.WorkerPayment;
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

    private final ReferenceDataService referenceDataService;

    public ReportService(ReferenceDataService referenceDataService) {
        this.referenceDataService = referenceDataService;
    }

    @Transactional(readOnly = true)
    public List<ReportDtos.StageCostRow> stageCosts(Long projectId) {
        Map<Long, BigDecimal> totals = new LinkedHashMap<>();
        referenceDataService.stagesByProject(projectId).forEach(stage -> totals.put(stage.getId(), BigDecimal.ZERO));
        referenceDataService.usageItemsByProject(projectId).forEach(item -> {
            if (item.getInvoice().getStage() != null) {
                totals.computeIfPresent(item.getInvoice().getStage().getId(), (key, value) -> value.add(item.getTotalAmount()));
            }
        });
        referenceDataService.workerPaymentsByProject(projectId).forEach(payment ->
                totals.computeIfPresent(payment.getStage().getId(), (key, value) -> value.add(payment.getAmount()))
        );
        return referenceDataService.stagesByProject(projectId).stream()
                .map(stage -> new ReportDtos.StageCostRow(stage.getId(), stage.getName(), totals.getOrDefault(stage.getId(), BigDecimal.ZERO)))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ReportDtos.CategoryCostRow> categoryCosts(Long projectId) {
        Map<Long, ReportDtos.CategoryCostRow> rows = new LinkedHashMap<>();
        referenceDataService.usageItemsByProject(projectId).forEach(item -> rows.merge(
                item.getCategory().getId(),
                new ReportDtos.CategoryCostRow(item.getCategory().getId(), item.getCategory().getName(), item.getTotalAmount()),
                (left, right) -> new ReportDtos.CategoryCostRow(left.categoryId(), left.categoryName(), left.totalCost().add(right.totalCost()))
        ));
        referenceDataService.workerPaymentsByProject(projectId).forEach(payment -> {
            Long workerCategoryKey = -1L - payment.getWorker().getType().ordinal();
            String workerCategoryName = "Workers - " + payment.getWorker().getType().name();
            rows.merge(
                    workerCategoryKey,
                    new ReportDtos.CategoryCostRow(workerCategoryKey, workerCategoryName, payment.getAmount()),
                    (left, right) -> new ReportDtos.CategoryCostRow(left.categoryId(), left.categoryName(), left.totalCost().add(right.totalCost()))
            );
        });
        return new ArrayList<>(rows.values());
    }

    @Transactional(readOnly = true)
    public List<ReportDtos.SupplierBalanceRow> supplierBalances(Long projectId) {
        Map<Long, ReportDtos.SupplierBalanceRow> rows = new LinkedHashMap<>();
        List<SupplierInvoice> invoices = referenceDataService.invoicesByProject(projectId);
        for (SupplierInvoice invoice : invoices) {
            BigDecimal consumed = referenceDataService.invoiceConsumedAmount(invoice.getId());
            BigDecimal outgoing = referenceDataService.invoiceOutgoingAmount(invoice.getId());
            rows.merge(
                    invoice.getSupplier().getId(),
                    new ReportDtos.SupplierBalanceRow(invoice.getSupplier().getId(), invoice.getSupplier().getName(), invoice.getTotalAmount(), consumed, invoice.getTotalAmount().subtract(outgoing)),
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
