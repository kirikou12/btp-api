package mr.btp.api.worker;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public final class WorkerDtos {

    private WorkerDtos() {
    }

    public record WorkerRequest(
            @NotBlank String name,
            @NotNull WorkerType type,
            @NotNull @DecimalMin("0.00") BigDecimal plannedBudget
    ) {
    }

    public record WorkerResponse(
            Long id,
            String name,
            WorkerType type,
            BigDecimal plannedBudget,
            BigDecimal paidAmount,
            BigDecimal remainingBudget,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public record WorkerPaymentRequest(
            @NotNull Long workerId,
            @NotNull Long projectId,
            @NotNull Long stageId,
            @NotNull @DecimalMin("0.00") BigDecimal amount,
            @NotNull LocalDate paymentDate,
            String documentRef
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
            String documentRef
    ) {
    }
}
