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
            @NotBlank String name,
            @NotBlank String location,
            String description,
            @NotNull LocalDate startDate,
            @NotNull @DecimalMin("0.00") BigDecimal estimatedSalePrice,
            @NotNull @DecimalMin("0.00") BigDecimal budget,
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
            @NotNull StageStatus status,
            LocalDate startDate,
            LocalDate endDate,
            @DecimalMin("0.00") BigDecimal plannedBudget,
            @Min(0) @Max(100) Integer progressPercent
    ) {
    }

    public record StageResponse(
            Long id,
            Long projectId,
            Long stageTemplateId,
            String name,
            Integer sortOrder,
            String status,
            LocalDate startDate,
            LocalDate endDate,
            BigDecimal plannedBudget,
            BigDecimal actualCost,
            Integer progressPercent
    ) {
    }

    public record StageTemplateRequest(
            @NotBlank String name,
            @NotNull Integer sortOrder,
            boolean active
    ) {
    }

    public record StageTemplateResponse(
            Long id,
            String name,
            Integer sortOrder,
            boolean active,
            Instant createdAt,
            Instant updatedAt
    ) {
    }
}
