package mr.btp.api.expense;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import mr.btp.api.common.dto.PageResponse;
import mr.btp.api.common.service.ReferenceDataService;
import mr.btp.api.invoice.InvoiceType;
import mr.btp.api.invoice.SupplierInvoice;
import mr.btp.api.invoice.SupplierInvoiceItem;
import mr.btp.api.invoice.SupplierInvoiceItemRepository;
import mr.btp.api.invoice.SupplierInvoiceRepository;
import mr.btp.api.worker.WorkerPayment;
import mr.btp.api.worker.WorkerPaymentRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExpenseService {

    private static final int DEFAULT_PAGE_SIZE = 30;
    private static final int MAX_PAGE_SIZE = 100;
    private static final List<InvoiceType> EXPENSE_INVOICE_TYPES = List.of(
            InvoiceType.DIRECT_EXPENSE,
            InvoiceType.DIRECT_USAGE,
            InvoiceType.SUPPLY_USAGE
    );

    private final SupplierInvoiceRepository invoiceRepository;
    private final SupplierInvoiceItemRepository invoiceItemRepository;
    private final WorkerPaymentRepository workerPaymentRepository;
    private final ReferenceDataService referenceDataService;

    public ExpenseService(SupplierInvoiceRepository invoiceRepository,
                          SupplierInvoiceItemRepository invoiceItemRepository,
                          WorkerPaymentRepository workerPaymentRepository,
                          ReferenceDataService referenceDataService) {
        this.invoiceRepository = invoiceRepository;
        this.invoiceItemRepository = invoiceItemRepository;
        this.workerPaymentRepository = workerPaymentRepository;
        this.referenceDataService = referenceDataService;
    }

    @Transactional(readOnly = true)
    public PageResponse<ExpenseDtos.ProjectExpenseResponse> listProjectExpenses(Long projectId,
                                                                                 int page,
                                                                                 int size,
                                                                                 List<Long> stageIds,
                                                                                 List<Long> workerIds,
                                                                                 List<Long> supplierIds,
                                                                                 List<Long> categoryIds) {
        referenceDataService.getProject(projectId);

        int safePage = Math.max(page, 0);
        int safeSize = size <= 0 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
        int windowSize = Math.max((safePage + 1) * safeSize, safeSize);
        List<Long> normalizedStageIds = normalizeIds(stageIds);
        List<Long> normalizedWorkerIds = normalizeIds(workerIds);
        List<Long> normalizedSupplierIds = normalizeIds(supplierIds);
        List<Long> normalizedCategoryIds = normalizeIds(categoryIds);

        Page<SupplierInvoice> invoicePage = Page.empty();
        if (normalizedWorkerIds.isEmpty()) {
            invoicePage = invoiceRepository.findProjectExpenseInvoices(
                    projectId,
                    EXPENSE_INVOICE_TYPES,
                    normalizedStageIds.isEmpty(),
                    queryIds(normalizedStageIds),
                    normalizedSupplierIds.isEmpty(),
                    queryIds(normalizedSupplierIds),
                    normalizedCategoryIds.isEmpty(),
                    queryIds(normalizedCategoryIds),
                    PageRequest.of(0, windowSize)
            );
        }

        Page<WorkerPayment> paymentPage = Page.empty();
        if (normalizedSupplierIds.isEmpty() && normalizedCategoryIds.isEmpty()) {
            paymentPage = workerPaymentRepository.findProjectExpensePayments(
                    projectId,
                    normalizedStageIds.isEmpty(),
                    queryIds(normalizedStageIds),
                    normalizedWorkerIds.isEmpty(),
                    queryIds(normalizedWorkerIds),
                    PageRequest.of(0, windowSize)
            );
        }

        List<ExpenseDtos.ProjectExpenseResponse> combined = mergeAndSlice(
                toInvoiceExpenseResponses(invoicePage.getContent()),
                toWorkerPaymentExpenseResponses(paymentPage.getContent()),
                safePage,
                safeSize
        );
        long totalElements = invoicePage.getTotalElements() + paymentPage.getTotalElements();
        int totalPages = totalElements == 0 ? 0 : (int) Math.ceil((double) totalElements / safeSize);

        return new PageResponse<>(combined, safePage, safeSize, totalElements, totalPages);
    }

    private List<ExpenseDtos.ProjectExpenseResponse> mergeAndSlice(List<ExpenseDtos.ProjectExpenseResponse> invoices,
                                                                    List<ExpenseDtos.ProjectExpenseResponse> payments,
                                                                    int page,
                                                                    int size) {
        int fromIndex = page * size;
        return java.util.stream.Stream.concat(invoices.stream(), payments.stream())
                .sorted(Comparator
                        .comparing(ExpenseDtos.ProjectExpenseResponse::date, Comparator.reverseOrder())
                        .thenComparing(ExpenseDtos.ProjectExpenseResponse::id, Comparator.reverseOrder()))
                .skip(fromIndex)
                .limit(size)
                .toList();
    }

    private List<ExpenseDtos.ProjectExpenseResponse> toInvoiceExpenseResponses(List<SupplierInvoice> invoices) {
        if (invoices.isEmpty()) {
            return List.of();
        }

        List<Long> invoiceIds = invoices.stream().map(SupplierInvoice::getId).toList();
        Map<Long, List<SupplierInvoiceItem>> itemsByInvoiceId = invoiceItemRepository.findDetailedByInvoiceIdIn(invoiceIds).stream()
                .collect(Collectors.groupingBy(item -> item.getInvoice().getId()));

        return invoices.stream()
                .map(invoice -> toInvoiceExpenseResponse(invoice, itemsByInvoiceId.getOrDefault(invoice.getId(), List.of())))
                .toList();
    }

    private ExpenseDtos.ProjectExpenseResponse toInvoiceExpenseResponse(SupplierInvoice invoice, List<SupplierInvoiceItem> items) {
        List<Long> categoryIds = items.stream()
                .map(item -> item.getCategory().getId())
                .distinct()
                .toList();
        String firstCategoryName = items.stream()
                .map(item -> item.getCategory().getName())
                .findFirst()
                .orElse(null);
        String itemDescriptions = items.stream()
                .map(SupplierInvoiceItem::getDescription)
                .filter(description -> description != null && !description.isBlank())
                .distinct()
                .collect(Collectors.joining(", "));
        String description = invoice.getSupplier() != null
                ? invoice.getSupplier().getName()
                : !itemDescriptions.isBlank() ? itemDescriptions : fallbackInvoiceLabel(invoice.getInvoiceType());
        String category = invoice.getNotes() != null && !invoice.getNotes().isBlank()
                ? invoice.getNotes()
                : invoice.getReference() != null && !invoice.getReference().isBlank() ? invoice.getReference() : firstCategoryName;

        return new ExpenseDtos.ProjectExpenseResponse(
                invoice.getId(),
                invoice.getInvoiceType() == InvoiceType.DIRECT_EXPENSE ? "direct-expense-invoice" : "usage-invoice",
                invoice.getInvoiceDate(),
                category == null ? fallbackInvoiceLabel(invoice.getInvoiceType()) : category,
                description,
                invoice.getTotalAmount(),
                invoice.getStage() == null ? null : invoice.getStage().getId(),
                invoice.getStage() == null ? null : invoice.getStage().getName(),
                invoice.getInvoiceType(),
                invoice.getSupplier() == null ? null : invoice.getSupplier().getId(),
                invoice.getSupplier() == null ? null : invoice.getSupplier().getName(),
                null,
                null,
                null,
                categoryIds
        );
    }

    private List<ExpenseDtos.ProjectExpenseResponse> toWorkerPaymentExpenseResponses(List<WorkerPayment> payments) {
        return payments.stream()
                .map(payment -> new ExpenseDtos.ProjectExpenseResponse(
                        payment.getId(),
                        "worker-payment",
                        payment.getPaymentDate(),
                        "Labor - " + payment.getWorker().getType().name(),
                        payment.getWorker().getName(),
                        payment.getAmount(),
                        payment.getStage().getId(),
                        payment.getStage().getName(),
                        null,
                        null,
                        null,
                        payment.getWorker().getId(),
                        payment.getWorker().getName(),
                        payment.getWorker().getType(),
                        List.of()
                ))
                .toList();
    }

    private String fallbackInvoiceLabel(InvoiceType invoiceType) {
        if (invoiceType == InvoiceType.DIRECT_EXPENSE) {
            return "Direct Expense";
        }
        if (invoiceType == InvoiceType.DIRECT_USAGE) {
            return "Direct Invoice";
        }
        return "Usage Invoice";
    }

    private List<Long> normalizeIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return ids.stream()
                .filter(id -> id != null)
                .distinct()
                .toList();
    }

    private List<Long> queryIds(List<Long> ids) {
        return ids.isEmpty() ? List.of(-1L) : ids;
    }
}
