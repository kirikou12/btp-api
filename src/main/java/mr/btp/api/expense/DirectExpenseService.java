package mr.btp.api.expense;

import mr.btp.api.category.CategoryType;
import mr.btp.api.common.exception.ApiException;
import mr.btp.api.common.service.ReferenceDataService;
import mr.btp.api.project.ConstructionStage;
import mr.btp.api.project.StageStatus;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class DirectExpenseService {

    private final DirectExpenseRepository expenseRepository;
    private final ReferenceDataService referenceDataService;

    public DirectExpenseService(DirectExpenseRepository expenseRepository, ReferenceDataService referenceDataService) {
        this.expenseRepository = expenseRepository;
        this.referenceDataService = referenceDataService;
    }

    public List<ExpenseDtos.ExpenseResponse> byProject(Long projectId) {
        referenceDataService.getProject(projectId);
        return referenceDataService.expensesByProject(projectId).stream().map(this::toResponse).toList();
    }

    public ExpenseDtos.ExpenseResponse get(Long id) {
        return toResponse(referenceDataService.getExpense(id));
    }

    @Transactional
    public ExpenseDtos.ExpenseResponse create(ExpenseDtos.ExpenseRequest request) {
        DirectExpense expense = new DirectExpense();
        apply(expense, request);
        return toResponse(expenseRepository.save(expense));
    }

    @Transactional
    public ExpenseDtos.ExpenseResponse update(Long id, ExpenseDtos.ExpenseRequest request) {
        DirectExpense expense = referenceDataService.getExpense(id);
        apply(expense, request);
        return toResponse(expenseRepository.save(expense));
    }

    @Transactional
    public void delete(Long id) {
        if (!expenseRepository.existsById(id)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Expense not found");
        }
        expenseRepository.deleteById(id);
    }

    private void apply(DirectExpense expense, ExpenseDtos.ExpenseRequest request) {
        expense.setProject(referenceDataService.getProject(request.projectId()));
        ConstructionStage stage = request.stageId() == null ? null : referenceDataService.getStage(request.stageId());
        if (stage != null && !stage.getProject().getId().equals(request.projectId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Stage must belong to the same project");
        }
        if (stage != null && stage.getStatus() == StageStatus.COMPLETED) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Cannot record a direct expense on a completed stage");
        }
        if (request.subCategory() != null && !request.subCategory().isBlank() && referenceDataService.getCategory(request.categoryId()).getType() != CategoryType.LABOR) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Sub-category is only allowed for labor expenses");
        }
        expense.setStage(stage);
        expense.setCategory(referenceDataService.getCategory(request.categoryId()));
        expense.setSupplier(request.supplierId() == null ? null : referenceDataService.getSupplier(request.supplierId()));
        expense.setAmount(request.amount());
        expense.setDescription(request.description().trim());
        expense.setSubCategory(request.subCategory() == null || request.subCategory().isBlank() ? null : request.subCategory().trim());
        expense.setDocumentRef(request.documentRef() == null || request.documentRef().isBlank() ? null : request.documentRef().trim());
        expense.setExpenseDate(request.expenseDate());
    }

    private ExpenseDtos.ExpenseResponse toResponse(DirectExpense expense) {
        return new ExpenseDtos.ExpenseResponse(
                expense.getId(),
                expense.getProject().getId(),
                expense.getStage() == null ? null : expense.getStage().getId(),
                expense.getCategory().getId(),
                expense.getSupplier() == null ? null : expense.getSupplier().getId(),
                expense.getCategory().getName(),
                expense.getAmount(),
                expense.getDescription(),
                expense.getSubCategory(),
                expense.getDocumentRef(),
                expense.getExpenseDate()
        );
    }
}
