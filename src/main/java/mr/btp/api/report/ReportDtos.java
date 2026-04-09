package mr.btp.api.report;

import java.math.BigDecimal;
import java.time.LocalDate;

public final class ReportDtos {

    private ReportDtos() {
    }

    public record StageCostRow(Long stageId, String stageName, BigDecimal totalCost) {
    }

    public record CategoryCostRow(Long categoryId, String categoryName, BigDecimal totalCost) {
    }

    public record SupplierBalanceRow(Long supplierId, String supplierName, BigDecimal totalInvoiced, BigDecimal totalConsumed, BigDecimal remainingBalance) {
    }

    public record ActivityFeedRow(String type, String title, BigDecimal amount, LocalDate activityDate, String details) {
    }
}
