package mr.btp.api.common.service;

import mr.btp.api.category.ExpenseCategory;
import mr.btp.api.category.ExpenseCategoryRepository;
import mr.btp.api.common.exception.ApiException;
import mr.btp.api.consumption.MaterialConsumption;
import mr.btp.api.consumption.MaterialConsumptionRepository;
import mr.btp.api.expense.DirectExpense;
import mr.btp.api.expense.DirectExpenseRepository;
import mr.btp.api.invoice.SupplierInvoice;
import mr.btp.api.invoice.SupplierInvoiceItem;
import mr.btp.api.invoice.SupplierInvoiceItemRepository;
import mr.btp.api.invoice.SupplierInvoiceRepository;
import mr.btp.api.project.ConstructionStage;
import mr.btp.api.project.ConstructionStageRepository;
import mr.btp.api.project.Project;
import mr.btp.api.project.ProjectRepository;
import mr.btp.api.supplier.Supplier;
import mr.btp.api.supplier.SupplierRepository;
import mr.btp.api.user.User;
import mr.btp.api.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
public class ReferenceDataService {

    private final UserRepository userRepository;
    private final ProjectRepository projectRepository;
    private final ConstructionStageRepository stageRepository;
    private final ExpenseCategoryRepository categoryRepository;
    private final SupplierRepository supplierRepository;
    private final DirectExpenseRepository expenseRepository;
    private final SupplierInvoiceRepository invoiceRepository;
    private final SupplierInvoiceItemRepository invoiceItemRepository;
    private final MaterialConsumptionRepository consumptionRepository;

    public ReferenceDataService(UserRepository userRepository,
                                ProjectRepository projectRepository,
                                ConstructionStageRepository stageRepository,
                                ExpenseCategoryRepository categoryRepository,
                                SupplierRepository supplierRepository,
                                DirectExpenseRepository expenseRepository,
                                SupplierInvoiceRepository invoiceRepository,
                                SupplierInvoiceItemRepository invoiceItemRepository,
                                MaterialConsumptionRepository consumptionRepository) {
        this.userRepository = userRepository;
        this.projectRepository = projectRepository;
        this.stageRepository = stageRepository;
        this.categoryRepository = categoryRepository;
        this.supplierRepository = supplierRepository;
        this.expenseRepository = expenseRepository;
        this.invoiceRepository = invoiceRepository;
        this.invoiceItemRepository = invoiceItemRepository;
        this.consumptionRepository = consumptionRepository;
    }

    public User getUser(Long id) {
        return userRepository.findById(id).orElseThrow(() -> notFound("User"));
    }

    public Project getProject(Long id) {
        return projectRepository.findById(id).orElseThrow(() -> notFound("Project"));
    }

    public ConstructionStage getStage(Long id) {
        return stageRepository.findById(id).orElseThrow(() -> notFound("Stage"));
    }

    public ExpenseCategory getCategory(Long id) {
        return categoryRepository.findById(id).orElseThrow(() -> notFound("Category"));
    }

    public Supplier getSupplier(Long id) {
        return supplierRepository.findById(id).orElseThrow(() -> notFound("Supplier"));
    }

    public DirectExpense getExpense(Long id) {
        return expenseRepository.findById(id).orElseThrow(() -> notFound("Expense"));
    }

    public SupplierInvoice getInvoice(Long id) {
        return invoiceRepository.findById(id).orElseThrow(() -> notFound("Supplier invoice"));
    }

    public SupplierInvoiceItem getInvoiceItem(Long id) {
        return invoiceItemRepository.findById(id).orElseThrow(() -> notFound("Supplier invoice item"));
    }

    public MaterialConsumption getConsumption(Long id) {
        return consumptionRepository.findById(id).orElseThrow(() -> notFound("Material consumption"));
    }

    public BigDecimal invoiceItemConsumedAmount(Long invoiceItemId, Long excludingConsumptionId) {
        return consumptionRepository.findByInvoiceItemId(invoiceItemId).stream()
                .filter(consumption -> excludingConsumptionId == null || !consumption.getId().equals(excludingConsumptionId))
                .map(MaterialConsumption::getAmountUsed)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal invoiceConsumedAmount(Long invoiceId) {
        return invoiceItemRepository.findByInvoiceId(invoiceId).stream()
                .map(SupplierInvoiceItem::getId)
                .map(itemId -> invoiceItemConsumedAmount(itemId, null))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public List<ConstructionStage> stagesByProject(Long projectId) {
        return stageRepository.findByProjectIdOrderBySortOrderAsc(projectId);
    }

    public List<DirectExpense> expensesByProject(Long projectId) {
        return expenseRepository.findByProjectIdOrderByExpenseDateDesc(projectId);
    }

    public List<MaterialConsumption> consumptionsByProject(Long projectId) {
        return consumptionRepository.findByProjectIdOrderByConsumptionDateDesc(projectId);
    }

    public List<SupplierInvoice> invoicesByProject(Long projectId) {
        return invoiceRepository.findByProjectIdOrProjectIdIsNullOrderByInvoiceDateDesc(projectId);
    }

    private ApiException notFound(String resource) {
        return new ApiException(HttpStatus.NOT_FOUND, resource + " not found");
    }
}
