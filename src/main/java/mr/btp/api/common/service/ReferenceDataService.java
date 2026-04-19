package mr.btp.api.common.service;

import mr.btp.api.category.ExpenseCategory;
import mr.btp.api.category.ExpenseCategoryRepository;
import mr.btp.api.common.exception.ApiException;
import mr.btp.api.expense.DirectExpense;
import mr.btp.api.expense.DirectExpenseRepository;
import mr.btp.api.invoice.SupplierInvoice;
import mr.btp.api.invoice.SupplierInvoiceItem;
import mr.btp.api.invoice.SupplierInvoiceItemRepository;
import mr.btp.api.invoice.SupplierInvoiceRepository;
import mr.btp.api.invoice.InvoiceType;
import mr.btp.api.project.ConstructionStage;
import mr.btp.api.project.ConstructionStageRepository;
import mr.btp.api.project.Project;
import mr.btp.api.project.ProjectRepository;
import mr.btp.api.supplier.Supplier;
import mr.btp.api.supplier.SupplierRepository;
import mr.btp.api.user.User;
import mr.btp.api.user.UserRepository;
import mr.btp.api.worker.Worker;
import mr.btp.api.worker.WorkerPayment;
import mr.btp.api.worker.WorkerPaymentRepository;
import mr.btp.api.worker.WorkerRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
public class ReferenceDataService {

    private static final List<InvoiceType> USAGE_INVOICE_TYPES = List.of(InvoiceType.SUPPLY_USAGE, InvoiceType.DIRECT_USAGE);

    private final UserRepository userRepository;
    private final ProjectRepository projectRepository;
    private final ConstructionStageRepository stageRepository;
    private final ExpenseCategoryRepository categoryRepository;
    private final SupplierRepository supplierRepository;
    private final DirectExpenseRepository expenseRepository;
    private final SupplierInvoiceRepository invoiceRepository;
    private final SupplierInvoiceItemRepository invoiceItemRepository;
    private final WorkerRepository workerRepository;
    private final WorkerPaymentRepository workerPaymentRepository;

    public ReferenceDataService(UserRepository userRepository,
                                ProjectRepository projectRepository,
                                ConstructionStageRepository stageRepository,
                                ExpenseCategoryRepository categoryRepository,
                                SupplierRepository supplierRepository,
                                DirectExpenseRepository expenseRepository,
                                SupplierInvoiceRepository invoiceRepository,
                                SupplierInvoiceItemRepository invoiceItemRepository,
                                WorkerRepository workerRepository,
                                WorkerPaymentRepository workerPaymentRepository) {
        this.userRepository = userRepository;
        this.projectRepository = projectRepository;
        this.stageRepository = stageRepository;
        this.categoryRepository = categoryRepository;
        this.supplierRepository = supplierRepository;
        this.expenseRepository = expenseRepository;
        this.invoiceRepository = invoiceRepository;
        this.invoiceItemRepository = invoiceItemRepository;
        this.workerRepository = workerRepository;
        this.workerPaymentRepository = workerPaymentRepository;
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

    public Worker getWorker(Long id) {
        return workerRepository.findById(id).orElseThrow(() -> notFound("Worker"));
    }

    public WorkerPayment getWorkerPayment(Long id) {
        return workerPaymentRepository.findById(id).orElseThrow(() -> notFound("Worker payment"));
    }

    public BigDecimal invoiceItemOutgoingAmount(Long invoiceItemId, Long excludingInvoiceId) {
        return invoiceItemAmountByType(invoiceItemId, excludingInvoiceId, List.of(InvoiceType.SUPPLY_USAGE, InvoiceType.SUPPLY_RETURN));
    }

    public BigDecimal invoiceItemConsumedAmount(Long invoiceItemId, Long excludingInvoiceId) {
        return invoiceItemAmountByType(invoiceItemId, excludingInvoiceId, List.of(InvoiceType.SUPPLY_USAGE));
    }

    public BigDecimal invoiceItemReturnedAmount(Long invoiceItemId, Long excludingInvoiceId) {
        return invoiceItemAmountByType(invoiceItemId, excludingInvoiceId, List.of(InvoiceType.SUPPLY_RETURN));
    }

    private BigDecimal invoiceItemAmountByType(Long invoiceItemId, Long excludingInvoiceId, List<InvoiceType> invoiceTypes) {
        return invoiceItemRepository.findBySourceSupplyItemId(invoiceItemId).stream()
                .filter(item -> invoiceTypes.contains(item.getInvoice().getInvoiceType()))
                .filter(item -> excludingInvoiceId == null || !item.getInvoice().getId().equals(excludingInvoiceId))
                .map(SupplierInvoiceItem::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal invoiceConsumedAmount(Long invoiceId) {
        return invoiceItemRepository.findByInvoiceId(invoiceId).stream()
                .map(SupplierInvoiceItem::getId)
                .map(itemId -> invoiceItemConsumedAmount(itemId, null))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal invoiceOutgoingAmount(Long invoiceId) {
        return invoiceItemRepository.findByInvoiceId(invoiceId).stream()
                .map(SupplierInvoiceItem::getId)
                .map(itemId -> invoiceItemOutgoingAmount(itemId, null))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public List<ConstructionStage> stagesByProject(Long projectId) {
        return stageRepository.findByProjectIdOrderBySortOrderAsc(projectId);
    }

    public List<DirectExpense> expensesByProject(Long projectId) {
        return expenseRepository.findByProjectIdOrderByExpenseDateDesc(projectId);
    }

    public List<WorkerPayment> workerPaymentsByProject(Long projectId) {
        return workerPaymentRepository.findByStage_Project_IdOrderByPaymentDateDesc(projectId);
    }

    public List<SupplierInvoice> invoicesByProject(Long projectId) {
        return invoiceRepository.findSupplyInvoicesForProject(projectId);
    }

    public List<SupplierInvoiceItem> usageItemsByProject(Long projectId) {
        return invoiceRepository.findByProjectIdAndInvoiceTypeInOrderByInvoiceDateDescIdDesc(projectId, USAGE_INVOICE_TYPES).stream()
                .flatMap(invoice -> invoiceItemRepository.findByInvoiceId(invoice.getId()).stream())
                .toList();
    }

    public List<SupplierInvoice> usageInvoicesByProject(Long projectId) {
        return invoiceRepository.findByProjectIdAndInvoiceTypeInOrderByInvoiceDateDescIdDesc(projectId, USAGE_INVOICE_TYPES);
    }

    public BigDecimal stageActualCost(Long stageId) {
        BigDecimal expenses = expenseRepository.findAll().stream()
                .filter(expense -> expense.getStage() != null && expense.getStage().getId().equals(stageId))
                .map(DirectExpense::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal usageCost = USAGE_INVOICE_TYPES.stream()
                .flatMap(invoiceType -> invoiceRepository.findByInvoiceTypeOrderByInvoiceDateDesc(invoiceType).stream())
                .filter(invoice -> invoice.getStage() != null && invoice.getStage().getId().equals(stageId))
                .flatMap(invoice -> invoiceItemRepository.findByInvoiceId(invoice.getId()).stream())
                .map(SupplierInvoiceItem::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal workerPayments = workerPaymentRepository.findByStage_Id(stageId).stream()
                .map(WorkerPayment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return expenses.add(usageCost).add(workerPayments);
    }

    private ApiException notFound(String resource) {
        return new ApiException(HttpStatus.NOT_FOUND, resource + " not found");
    }
}
