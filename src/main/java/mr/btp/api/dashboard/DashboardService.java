package mr.btp.api.dashboard;

import mr.btp.api.category.CategoryType;
import mr.btp.api.common.service.ReferenceDataService;
import mr.btp.api.expense.DirectExpense;
import mr.btp.api.expense.DirectExpenseRepository;
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
import java.util.List;

@Service
public class DashboardService {

    private static final List<InvoiceType> USAGE_INVOICE_TYPES = List.of(InvoiceType.SUPPLY_USAGE, InvoiceType.DIRECT_USAGE);

    private final ProjectRepository projectRepository;
    private final DirectExpenseRepository expenseRepository;
    private final SupplierInvoiceRepository invoiceRepository;
    private final SupplierInvoiceItemRepository invoiceItemRepository;
    private final WorkerPaymentRepository workerPaymentRepository;
    private final ReferenceDataService referenceDataService;

    public DashboardService(ProjectRepository projectRepository,
                            DirectExpenseRepository expenseRepository,
                            SupplierInvoiceRepository invoiceRepository,
                            SupplierInvoiceItemRepository invoiceItemRepository,
                            WorkerPaymentRepository workerPaymentRepository,
                            ReferenceDataService referenceDataService) {
        this.projectRepository = projectRepository;
        this.expenseRepository = expenseRepository;
        this.invoiceRepository = invoiceRepository;
        this.invoiceItemRepository = invoiceItemRepository;
        this.workerPaymentRepository = workerPaymentRepository;
        this.referenceDataService = referenceDataService;
    }

    @Transactional(readOnly = true)
    public DashboardDtos.DashboardResponse global() {
        BigDecimal totalBudget = projectRepository.findAll().stream().map(Project::getBudget).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal estimatedSale = projectRepository.findAll().stream().map(Project::getEstimatedSalePrice).reduce(BigDecimal.ZERO, BigDecimal::add);
        List<DirectExpense> expenses = expenseRepository.findAll();
        BigDecimal materialExpenses = expenses.stream()
                .filter(this::isMaterialExpense)
                .map(DirectExpense::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal direct = expenses.stream()
                .filter(expense -> !isMaterialExpense(expense))
                .map(DirectExpense::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        List<SupplierInvoiceItem> usageItems = USAGE_INVOICE_TYPES.stream()
                .flatMap(invoiceType -> invoiceRepository.findByInvoiceTypeOrderByInvoiceDateDesc(invoiceType).stream())
                .flatMap(invoice -> invoiceItemRepository.findByInvoiceId(invoice.getId()).stream())
                .toList();
        BigDecimal usageMaterials = usageItems.stream()
                .filter(this::isMaterialInvoiceItem)
                .map(SupplierInvoiceItem::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal usageDirect = usageItems.stream()
                .filter(item -> !isMaterialInvoiceItem(item))
                .map(SupplierInvoiceItem::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal materials = materialExpenses.add(usageMaterials);
        direct = direct.add(usageDirect);
        BigDecimal workers = workerPaymentRepository.findAll().stream().map(payment -> payment.getAmount()).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal actual = direct.add(materials).add(workers);
        BigDecimal remainingSupplier = invoiceRepository.findByInvoiceTypeOrderByInvoiceDateDesc(InvoiceType.SUPPLY).stream()
                .flatMap(invoice -> invoiceItemRepository.findByInvoiceId(invoice.getId()).stream())
                .map(this::remainingForItem)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long active = projectRepository.findAll().stream().filter(project -> project.getStatus() == ProjectStatus.IN_PROGRESS).count();
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
        BigDecimal remainingSupplier = referenceDataService.invoicesByProject(projectId).stream()
                .flatMap(invoice -> invoiceItemRepository.findByInvoiceId(invoice.getId()).stream())
                .map(this::remainingForItem)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
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
        return projectRepository.findAll().stream()
                .map(project -> {
                    CostBreakdown costs = calculateProjectCosts(project.getId());
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
        List<DirectExpense> expenses = referenceDataService.expensesByProject(projectId);
        BigDecimal materialExpenses = expenses.stream()
                .filter(this::isMaterialExpense)
                .map(DirectExpense::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal direct = expenses.stream()
                .filter(expense -> !isMaterialExpense(expense))
                .map(DirectExpense::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        List<SupplierInvoiceItem> usageItems = referenceDataService.usageItemsByProject(projectId);
        BigDecimal usageMaterials = usageItems.stream()
                .filter(this::isMaterialInvoiceItem)
                .map(SupplierInvoiceItem::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal usageDirect = usageItems.stream()
                .filter(item -> !isMaterialInvoiceItem(item))
                .map(SupplierInvoiceItem::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal materials = materialExpenses.add(usageMaterials);
        direct = direct.add(usageDirect);
        BigDecimal workers = referenceDataService.workerPaymentsByProject(projectId).stream().map(payment -> payment.getAmount()).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new CostBreakdown(direct, materials, workers);
    }

    private BigDecimal remainingForItem(SupplierInvoiceItem item) {
        return item.getTotalAmount().subtract(referenceDataService.invoiceItemOutgoingAmount(item.getId(), null));
    }

    private boolean isMaterialExpense(DirectExpense expense) {
        return expense.getCategory().getType() == CategoryType.MATERIAL;
    }

    private boolean isMaterialInvoiceItem(SupplierInvoiceItem item) {
        return item.getCategory().getType() == CategoryType.MATERIAL;
    }

    private record CostBreakdown(BigDecimal direct, BigDecimal materials, BigDecimal workers) {
    }
}
