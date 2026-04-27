package mr.btp.api.worker;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import mr.btp.api.document.DocumentDtos;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class WorkerDtos {

    private WorkerDtos() {
    }

    public record WorkerRequest(
            @NotBlank(message = "{validation.worker.name.required}") String name,
            @NotNull(message = "{validation.worker.type.required}") WorkerType type,
            Long projectId,
            List<Long> projectIds,
            @NotNull(message = "{validation.worker.planned-budget.required}") @DecimalMin(value = "0.00", message = "{validation.worker.planned-budget.non-negative}") BigDecimal plannedBudget,
            List<WorkerStageBudgetRequest> stageBudgets
    ) {
    }

    public record WorkerStageBudgetRequest(
            @NotNull(message = "{validation.worker.stage-id.required}") Long stageId,
            @NotNull(message = "{validation.worker.stage-budget.required}") @DecimalMin(value = "0.00", message = "{validation.worker.stage-budget.non-negative}") BigDecimal plannedBudget
    ) {
    }

    public record WorkerResponse(
            Long id,
            String name,
            WorkerType type,
            Long projectId,
            List<Long> projectIds,
            List<String> projectNames,
            BigDecimal plannedBudget,
            List<WorkerStageBudgetResponse> stageBudgets,
            BigDecimal paidAmount,
            BigDecimal remainingBudget,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public record WorkerStageBudgetResponse(
            Long stageId,
            String stageName,
            BigDecimal plannedBudget
    ) {
    }

    public record WorkerPaymentRequest(
            @NotNull(message = "{validation.worker-payment.worker.required}") Long workerId,
            @NotNull(message = "{validation.worker-payment.project.required}") Long projectId,
            @NotNull(message = "{validation.worker-payment.stage.required}") Long stageId,
            @NotNull(message = "{validation.worker-payment.amount.required}") @DecimalMin(value = "0.00", message = "{validation.worker-payment.amount.non-negative}") BigDecimal amount,
            @NotNull(message = "{validation.worker-payment.date.required}") LocalDate paymentDate,
            List<Long> documentIds
    ) {
    }

    public record WorkerPaymentResponse(
            Long id,
            Long workerId,
            String workerName,
            WorkerType workerType,
            Long projectId,
            Long stageId,
            String stageName,
            BigDecimal amount,
            LocalDate paymentDate,
            List<DocumentDtos.DocumentAttachmentResponse> documents
    ) {
    }
}
