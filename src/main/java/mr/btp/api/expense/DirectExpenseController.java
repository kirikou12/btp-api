package mr.btp.api.expense;

import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DirectExpenseController {

    private final DirectExpenseService directExpenseService;

    public DirectExpenseController(DirectExpenseService directExpenseService) {
        this.directExpenseService = directExpenseService;
    }

    @GetMapping("/api/projects/{id}/expenses")
    public List<ExpenseDtos.ExpenseResponse> listByProject(@PathVariable Long id) {
        return directExpenseService.byProject(id);
    }

    @PostMapping("/api/expenses")
    public ExpenseDtos.ExpenseResponse create(@Valid @RequestBody ExpenseDtos.ExpenseRequest request) {
        return directExpenseService.create(request);
    }

    @GetMapping("/api/expenses/{id}")
    public ExpenseDtos.ExpenseResponse get(@PathVariable Long id) {
        return directExpenseService.get(id);
    }

    @PutMapping("/api/expenses/{id}")
    public ExpenseDtos.ExpenseResponse update(@PathVariable Long id, @Valid @RequestBody ExpenseDtos.ExpenseRequest request) {
        return directExpenseService.update(id, request);
    }

    @DeleteMapping("/api/expenses/{id}")
    public void delete(@PathVariable Long id) {
        directExpenseService.delete(id);
    }
}
