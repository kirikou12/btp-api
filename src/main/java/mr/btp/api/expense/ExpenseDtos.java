package mr.btp.api.expense;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

public final class ExpenseDtos {

    private ExpenseDtos() {
    }

    public record ExpenseRequest(
            @NotNull Long projectId,
            Long stageId,
            @NotNull Long categoryId,
            Long supplierId,
            @NotNull @DecimalMin("0.00") BigDecimal amount,
            @NotBlank String description,
            @NotNull LocalDate expenseDate
    ) {
    }

    public record ExpenseResponse(
            Long id,
            Long projectId,
            Long stageId,
            Long categoryId,
            Long supplierId,
            String categoryName,
            BigDecimal amount,
            String description,
            LocalDate expenseDate
    ) {
    }
}
