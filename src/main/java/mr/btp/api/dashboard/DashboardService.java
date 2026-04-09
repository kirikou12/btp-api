package mr.btp.api.dashboard;

import mr.btp.api.common.service.ReferenceDataService;
import mr.btp.api.expense.DirectExpenseRepository;
import mr.btp.api.invoice.SupplierInvoiceItem;
import mr.btp.api.invoice.SupplierInvoiceItemRepository;
import mr.btp.api.project.Project;
import mr.btp.api.project.ProjectRepository;
import mr.btp.api.project.ProjectStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
public class DashboardService {

    private final ProjectRepository projectRepository;
    private final DirectExpenseRepository expenseRepository;
    private final SupplierInvoiceItemRepository invoiceItemRepository;
    private final ReferenceDataService referenceDataService;

    public DashboardService(ProjectRepository projectRepository,
                            DirectExpenseRepository expenseRepository,
                            SupplierInvoiceItemRepository invoiceItemRepository,
                            ReferenceDataService referenceDataService) {
        this.projectRepository = projectRepository;
        this.expenseRepository = expenseRepository;
        this.invoiceItemRepository = invoiceItemRepository;
        this.referenceDataService = referenceDataService;
    }

    @Transactional(readOnly = true)
    public DashboardDtos.DashboardResponse global() {
        BigDecimal totalBudget = projectRepository.findAll().stream().map(Project::getBudget).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal estimatedSale = projectRepository.findAll().stream().map(Project::getEstimatedSalePrice).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal direct = expenseRepository.findAll().stream().map(expense -> expense.getAmount()).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal material = invoiceItemRepository.findAll().stream()
                .map(item -> referenceDataService.invoiceItemConsumedAmount(item.getId(), null))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal actual = direct.add(material);
        BigDecimal remainingSupplier = invoiceItemRepository.findAll().stream()
                .map(this::remainingForItem)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long active = projectRepository.findAll().stream().filter(project -> project.getStatus() == ProjectStatus.IN_PROGRESS).count();
        return new DashboardDtos.DashboardResponse(
                direct, material, actual, totalBudget, totalBudget.subtract(actual), estimatedSale,
                estimatedSale.subtract(actual), active, remainingSupplier
        );
    }

    @Transactional(readOnly = true)
    public DashboardDtos.DashboardResponse byProject(Long projectId) {
        Project project = referenceDataService.getProject(projectId);
        BigDecimal direct = referenceDataService.expensesByProject(projectId).stream().map(expense -> expense.getAmount()).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal material = referenceDataService.consumptionsByProject(projectId).stream().map(consumption -> consumption.getAmountUsed()).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal actual = direct.add(material);
        BigDecimal remainingSupplier = referenceDataService.invoicesByProject(projectId).stream()
                .flatMap(invoice -> invoiceItemRepository.findByInvoiceId(invoice.getId()).stream())
                .map(this::remainingForItem)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new DashboardDtos.DashboardResponse(
                direct,
                material,
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
        return item.getTotalAmount().subtract(referenceDataService.invoiceItemConsumedAmount(item.getId(), null));
    }
}
