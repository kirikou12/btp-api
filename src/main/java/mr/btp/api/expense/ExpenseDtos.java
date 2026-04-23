package mr.btp.api.expense;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import mr.btp.api.invoice.InvoiceType;
import mr.btp.api.worker.WorkerType;

public final class ExpenseDtos {

    private ExpenseDtos() {
    }

    public record ProjectExpenseResponse(
            Long id,
            String kind,
            LocalDate date,
            String category,
            String description,
            BigDecimal amount,
            Long stageId,
            String stageName,
            InvoiceType invoiceType,
            Long supplierId,
            String supplierName,
            Long workerId,
            String workerName,
            WorkerType workerType,
            List<Long> categoryIds
    ) {
    }
}
