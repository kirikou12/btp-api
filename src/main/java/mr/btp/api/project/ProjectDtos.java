package mr.btp.api.project;

import jakarta.validation.constraints.DecimalMin;
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
            @NotNull ProjectStatus status
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
            @NotBlank String name,
            @NotNull Integer sortOrder,
            @NotNull StageStatus status,
            LocalDate startDate,
            LocalDate endDate,
            BigDecimal plannedBudget
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
            BigDecimal plannedBudget
    ) {
    }
}
