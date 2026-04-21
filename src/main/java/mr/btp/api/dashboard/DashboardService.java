package mr.btp.api.dashboard;

import mr.btp.api.category.CategoryType;
import mr.btp.api.common.service.ReferenceDataService;
import mr.btp.api.invoice.InvoiceType;
import mr.btp.api.invoice.SupplierInvoiceRepository;
import mr.btp.api.invoice.SupplierInvoiceItem;
import mr.btp.api.invoice.SupplierInvoiceItemRepository;
import mr.btp.api.project.Project;
import mr.btp.api.project.ProjectRepository;
import mr.btp.api.project.ProjectStatus;
import mr.btp.api.worker.WorkerPaymentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class DashboardService {

    private static final List<InvoiceType> USAGE_INVOICE_TYPES = List.of(InvoiceType.SUPPLY_USAGE, InvoiceType.DIRECT_USAGE, InvoiceType.DIRECT_EXPENSE);

    private final ProjectRepository projectRepository;
    private final SupplierInvoiceRepository invoiceRepository;
    private final SupplierInvoiceItemRepository invoiceItemRepository;
    private final WorkerPaymentRepository workerPaymentRepository;
    private final ReferenceDataService referenceDataService;

    public DashboardService(ProjectRepository projectRepository,
                            SupplierInvoiceRepository invoiceRepository,
                            SupplierInvoiceItemRepository invoiceItemRepository,
                            WorkerPaymentRepository workerPaymentRepository,
                            ReferenceDataService referenceDataService) {
        this.projectRepository = projectRepository;
        this.invoiceRepository = invoiceRepository;
        this.invoiceItemRepository = invoiceItemRepository;
        this.workerPaymentRepository = workerPaymentRepository;
        this.referenceDataService = referenceDataService;
    }

    @Transactional(readOnly = true)
    public DashboardDtos.DashboardResponse global() {
        ProjectRepository.ProjectTotals projectTotals = projectRepository.calculateTotals();
        BigDecimal totalBudget = projectTotals.getTotalBudget();
        BigDecimal estimatedSale = projectTotals.getEstimatedSalePrice();
        CostBreakdown usageCosts = usageCostBreakdown(null);
        BigDecimal materials = usageCosts.materials();
        BigDecimal direct = usageCosts.direct();
        BigDecimal workers = workerPaymentRepository.sumAmountByProjectId(null);
        BigDecimal actual = direct.add(materials).add(workers);
        BigDecimal remainingSupplier = remainingSupplierAmount(null);
        long active = projectTotals.getActiveProjects();
        return new DashboardDtos.DashboardResponse(
                direct, materials, workers, actual, totalBudget, totalBudget.subtract(actual), estimatedSale,
                estimatedSale.subtract(actual), active, remainingSupplier
        );
    }

    @Transactional(readOnly = true)
    public DashboardDtos.DashboardResponse byProject(Long projectId) {
        Project project = referenceDataService.getProject(projectId);
        CostBreakdown costs = calculateProjectCosts(projectId);
        BigDecimal actual = costs.direct().add(costs.materials()).add(costs.workers());
        BigDecimal remainingSupplier = remainingSupplierAmount(projectId);
        return new DashboardDtos.DashboardResponse(
                costs.direct(),
                costs.materials(),
                costs.workers(),
                actual,
                project.getBudget(),
                project.getBudget().subtract(actual),
                project.getEstimatedSalePrice(),
                project.getEstimatedSalePrice().subtract(actual),
                project.getStatus() == ProjectStatus.IN_PROGRESS ? 1 : 0,
                remainingSupplier
        );
    }

    @Transactional(readOnly = true)
    public List<DashboardDtos.ProjectExpenseSummaryResponse> projectExpenseSummaries() {
        Map<Long, CostBreakdown> costsByProject = calculateProjectCosts();
        return projectRepository.findAll().stream()
                .map(project -> {
                    CostBreakdown costs = costsByProject.getOrDefault(project.getId(), new CostBreakdown(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO));
                    BigDecimal actual = costs.direct().add(costs.materials()).add(costs.workers());
                    return new DashboardDtos.ProjectExpenseSummaryResponse(
                            project.getId(),
                            costs.direct(),
                            costs.materials(),
                            costs.workers(),
                            actual
                    );
                })
                .toList();
    }

    private CostBreakdown calculateProjectCosts(Long projectId) {
        CostBreakdown usageCosts = usageCostBreakdown(projectId);
        BigDecimal workers = workerPaymentRepository.sumAmountByProjectId(projectId);
        return new CostBreakdown(usageCosts.direct(), usageCosts.materials(), workers);
    }

    private Map<Long, CostBreakdown> calculateProjectCosts() {
        Map<Long, CostBreakdown> costsByProject = new HashMap<>();
        invoiceItemRepository.sumUsageTotalsByProjectAndCategoryType(USAGE_INVOICE_TYPES).forEach(row ->
                costsByProject.merge(
                        row.getProjectId(),
                        costFromCategoryType(row.getCategoryType(), row.getTotalAmount(), BigDecimal.ZERO),
                        CostBreakdown::add
                )
        );
        workerPaymentRepository.sumAmountsByProject().forEach(row ->
                costsByProject.merge(
                        row.getProjectId(),
                        new CostBreakdown(BigDecimal.ZERO, BigDecimal.ZERO, row.getTotalAmount()),
                        CostBreakdown::add
                )
        );
        return costsByProject;
    }

    private CostBreakdown usageCostBreakdown(Long projectId) {
        return invoiceItemRepository.sumUsageTotalsByCategoryType(projectId, USAGE_INVOICE_TYPES).stream()
                .map(row -> costFromCategoryType(row.getCategoryType(), row.getTotalAmount(), BigDecimal.ZERO))
                .reduce(new CostBreakdown(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO), CostBreakdown::add);
    }

    private CostBreakdown costFromCategoryType(CategoryType categoryType, BigDecimal totalAmount, BigDecimal workers) {
        if (categoryType == CategoryType.MATERIAL) {
            return new CostBreakdown(BigDecimal.ZERO, totalAmount, workers);
        }
        return new CostBreakdown(totalAmount, BigDecimal.ZERO, workers);
    }

    private BigDecimal remainingSupplierAmount(Long projectId) {
        List<Long> supplyInvoiceIds = (projectId == null
                ? invoiceRepository.findByInvoiceTypeOrderByInvoiceDateDesc(InvoiceType.SUPPLY)
                : referenceDataService.invoicesByProject(projectId)).stream()
                .map(invoice -> invoice.getId())
                .toList();
        if (supplyInvoiceIds.isEmpty()) {
            return BigDecimal.ZERO;
        }

        List<SupplierInvoiceItem> supplyItems = invoiceItemRepository.findDetailedByInvoiceIdIn(supplyInvoiceIds);
        List<Long> supplyItemIds = supplyItems.stream().map(SupplierInvoiceItem::getId).toList();
        Map<Long, BigDecimal> outgoingBySourceItem = invoiceItemRepository
                .sumOutgoingBySourceItemIds(supplyItemIds, List.of(InvoiceType.SUPPLY_USAGE, InvoiceType.SUPPLY_RETURN), null)
                .stream()
                .collect(java.util.stream.Collectors.toMap(
                        SupplierInvoiceItemRepository.SourceItemUsageTotal::getSourceSupplyItemId,
                        SupplierInvoiceItemRepository.SourceItemUsageTotal::getTotalAmount
                ));
        return supplyItems.stream()
                .map(item -> item.getTotalAmount().subtract(outgoingBySourceItem.getOrDefault(item.getId(), BigDecimal.ZERO)))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private record CostBreakdown(BigDecimal direct, BigDecimal materials, BigDecimal workers) {
        private CostBreakdown add(CostBreakdown other) {
            return new CostBreakdown(direct.add(other.direct), materials.add(other.materials), workers.add(other.workers));
        }
    }
}
