package mr.btp.api.consumption;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

public final class ConsumptionDtos {

    private ConsumptionDtos() {
    }

    public record ConsumptionRequest(
            @NotNull Long invoiceItemId,
            @NotNull Long projectId,
            @NotNull Long stageId,
            @NotNull Long categoryId,
            BigDecimal quantityUsed,
            @NotNull @DecimalMin("0.00") BigDecimal amountUsed,
            @NotNull LocalDate consumptionDate,
            String notes
    ) {
    }

    public record ConsumptionResponse(
            Long id,
            Long invoiceItemId,
            Long projectId,
            Long stageId,
            Long categoryId,
            String categoryName,
            BigDecimal quantityUsed,
            BigDecimal amountUsed,
            LocalDate consumptionDate,
            String notes
    ) {
    }
}
