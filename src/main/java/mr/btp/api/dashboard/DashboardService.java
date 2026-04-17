package mr.btp.api.dashboard;

import mr.btp.api.common.service.ReferenceDataService;
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
        BigDecimal direct = expenseRepository.findAll().stream().map(expense -> expense.getAmount()).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal usageCost = USAGE_INVOICE_TYPES.stream()
                .flatMap(invoiceType -> invoiceRepository.findByInvoiceTypeOrderByInvoiceDateDesc(invoiceType).stream())
                .flatMap(invoice -> invoiceItemRepository.findByInvoiceId(invoice.getId()).stream())
                .map(SupplierInvoiceItem::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal workers = workerPaymentRepository.findAll().stream().map(payment -> payment.getAmount()).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal actual = direct.add(usageCost).add(workers);
        BigDecimal remainingSupplier = invoiceRepository.findByInvoiceTypeOrderByInvoiceDateDesc(InvoiceType.SUPPLY).stream()
                .flatMap(invoice -> invoiceItemRepository.findByInvoiceId(invoice.getId()).stream())
                .map(this::remainingForItem)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long active = projectRepository.findAll().stream().filter(project -> project.getStatus() == ProjectStatus.IN_PROGRESS).count();
        return new DashboardDtos.DashboardResponse(
                direct, usageCost, workers, actual, totalBudget, totalBudget.subtract(actual), estimatedSale,
                estimatedSale.subtract(actual), active, remainingSupplier
        );
    }

    @Transactional(readOnly = true)
    public DashboardDtos.DashboardResponse byProject(Long projectId) {
        Project project = referenceDataService.getProject(projectId);
        BigDecimal direct = referenceDataService.expensesByProject(projectId).stream().map(expense -> expense.getAmount()).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal usageCost = referenceDataService.usageItemsByProject(projectId).stream().map(SupplierInvoiceItem::getTotalAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal workers = referenceDataService.workerPaymentsByProject(projectId).stream().map(payment -> payment.getAmount()).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal actual = direct.add(usageCost).add(workers);
        BigDecimal remainingSupplier = referenceDataService.invoicesByProject(projectId).stream()
                .flatMap(invoice -> invoiceItemRepository.findByInvoiceId(invoice.getId()).stream())
                .map(this::remainingForItem)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new DashboardDtos.DashboardResponse(
                direct,
                usageCost,
                workers,
                actual,
                project.getBudget(),
                project.getBudget().subtract(actual),
                project.getEstimatedSalePrice(),
                project.getEstimatedSalePrice().subtract(actual),
                project.getStatus() == ProjectStatus.IN_PROGRESS ? 1 : 0,
                remainingSupplier
        );
    }

    private BigDecimal remainingForItem(SupplierInvoiceItem item) {
        return item.getTotalAmount().subtract(referenceDataService.invoiceItemOutgoingAmount(item.getId(), null));
    }
}
