package mr.btp.api.project;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public final class ProjectDtos {

    private ProjectDtos() {
    }

    public record ProjectRequest(
            @NotBlank(message = "{validation.project.name.required}") String name,
            @NotBlank(message = "{validation.project.location.required}") String location,
            String description,
            @NotNull(message = "{validation.project.start-date.required}") LocalDate startDate,
            @NotNull(message = "{validation.project.estimated-sale-price.required}") @DecimalMin(value = "0.00", message = "{validation.project.estimated-sale-price.non-negative}") BigDecimal estimatedSalePrice,
            @NotNull(message = "{validation.project.budget.required}") @DecimalMin(value = "0.00", message = "{validation.project.budget.non-negative}") BigDecimal budget,
            ProjectStatus status
    ) {
    }

    public record ProjectResponse(
            Long id,
            String name,
            String location,
            String description,
            LocalDate startDate,
            BigDecimal estimatedSalePrice,
            BigDecimal budget,
            String status,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public record StageRequest(
            String name,
            @NotNull(message = "{validation.stage.status.required}") StageStatus status,
            LocalDate startDate,
            LocalDate endDate,
            @DecimalMin(value = "0.00", message = "{validation.stage.planned-budget.non-negative}") BigDecimal plannedBudget,
            @Min(value = 0, message = "{validation.stage.progress-percent.range}") @Max(value = 100, message = "{validation.stage.progress-percent.range}") Integer progressPercent,
            @Min(value = 1, message = "{validation.stage.sort-order.min}") Integer sortOrder
    ) {
    }

    public record StageCreateRequest(
            @NotBlank(message = "{validation.stage.name.required}") String name,
            @DecimalMin(value = "0.00", message = "{validation.stage.planned-budget.non-negative}") BigDecimal plannedBudget
    ) {
    }

    public record StageResponse(
            Long id,
            Long projectId,
            String name,
            Integer sortOrder,
            String status,
            LocalDate startDate,
            LocalDate endDate,
            BigDecimal plannedBudget,
            BigDecimal actualCost,
            Integer progressPercent,
            boolean excludedFromProjectStats,
            boolean deletable
    ) {
    }

}
