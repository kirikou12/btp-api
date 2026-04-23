package mr.btp.api.expense;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import mr.btp.api.common.dto.PageResponse;
import mr.btp.api.common.service.ReferenceDataService;
import mr.btp.api.invoice.InvoiceType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExpenseService {

    private static final int DEFAULT_PAGE_SIZE = 30;
    private static final int MAX_PAGE_SIZE = 100;

    private final ProjectExpenseEntryRepository expenseEntryRepository;
    private final ProjectExpenseEntryCategoryRepository expenseEntryCategoryRepository;
    private final ReferenceDataService referenceDataService;

    public ExpenseService(ProjectExpenseEntryRepository expenseEntryRepository,
                          ProjectExpenseEntryCategoryRepository expenseEntryCategoryRepository,
                          ReferenceDataService referenceDataService) {
        this.expenseEntryRepository = expenseEntryRepository;
        this.expenseEntryCategoryRepository = expenseEntryCategoryRepository;
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
        List<Long> normalizedStageIds = normalizeIds(stageIds);
        List<Long> normalizedWorkerIds = normalizeIds(workerIds);
        List<Long> normalizedSupplierIds = normalizeIds(supplierIds);
        List<Long> normalizedCategoryIds = normalizeIds(categoryIds);

        Page<ProjectExpenseEntry> expensePage = expenseEntryRepository.search(
                projectId,
                normalizedStageIds.isEmpty(),
                queryIds(normalizedStageIds),
                normalizedWorkerIds.isEmpty(),
                queryIds(normalizedWorkerIds),
                normalizedSupplierIds.isEmpty(),
                queryIds(normalizedSupplierIds),
                normalizedCategoryIds.isEmpty(),
                queryIds(normalizedCategoryIds),
                PageRequest.of(safePage, safeSize)
        );

        return new PageResponse<>(
                toResponses(expensePage.getContent()),
                expensePage.getNumber(),
                expensePage.getSize(),
                expensePage.getTotalElements(),
                expensePage.getTotalPages()
        );
    }

    private List<ExpenseDtos.ProjectExpenseResponse> toResponses(List<ProjectExpenseEntry> entries) {
        if (entries.isEmpty()) {
            return List.of();
        }

        Map<String, List<Long>> categoryIdsByEntryId = expenseEntryCategoryRepository.findByEntryIdIn(
                entries.stream().map(ProjectExpenseEntry::getId).toList()
        ).stream().collect(Collectors.groupingBy(
                ProjectExpenseEntryCategory::getEntryId,
                Collectors.mapping(ProjectExpenseEntryCategory::getCategoryId, Collectors.toList())
        ));

        return entries.stream()
                .map(entry -> toResponse(entry, categoryIdsByEntryId.getOrDefault(entry.getId(), List.of())))
                .toList();
    }

    private ExpenseDtos.ProjectExpenseResponse toResponse(ProjectExpenseEntry entry, List<Long> categoryIds) {
        return new ExpenseDtos.ProjectExpenseResponse(
                entry.getSourceId(),
                kind(entry),
                entry.getExpenseDate(),
                entry.getCategory(),
                entry.getDescription(),
                entry.getAmount(),
                entry.getStageId(),
                entry.getStageName(),
                entry.getInvoiceType(),
                entry.getSupplierId(),
                entry.getSupplierName(),
                entry.getWorkerId(),
                entry.getWorkerName(),
                entry.getWorkerType(),
                categoryIds
        );
    }

    private String kind(ProjectExpenseEntry entry) {
        if ("WORKER_PAYMENT".equals(entry.getSourceKind())) {
            return "worker-payment";
        }
        return entry.getInvoiceType() == InvoiceType.DIRECT_EXPENSE ? "direct-expense-invoice" : "usage-invoice";
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
