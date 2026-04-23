package mr.btp.api.expense;

import java.util.List;
import mr.btp.api.common.dto.PageResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ExpenseController {

    private final ExpenseService expenseService;

    public ExpenseController(ExpenseService expenseService) {
        this.expenseService = expenseService;
    }

    @GetMapping("/api/projects/{projectId}/expenses")
    public PageResponse<ExpenseDtos.ProjectExpenseResponse> listProjectExpenses(
            @PathVariable Long projectId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size,
            @RequestParam(required = false) List<Long> stageIds,
            @RequestParam(required = false) List<Long> workerIds,
            @RequestParam(required = false) List<Long> supplierIds,
            @RequestParam(required = false) List<Long> categoryIds
    ) {
        return expenseService.listProjectExpenses(projectId, page, size, stageIds, workerIds, supplierIds, categoryIds);
    }
}
