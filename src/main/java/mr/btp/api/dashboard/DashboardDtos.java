package mr.btp.api.dashboard;

import java.math.BigDecimal;

public final class DashboardDtos {

    private DashboardDtos() {
    }

    public record DashboardResponse(
            BigDecimal totalDirectExpenses,
            BigDecimal totalMaterialConsumed,
            BigDecimal totalActualCost,
            BigDecimal totalBudget,
            BigDecimal remainingBudget,
            BigDecimal estimatedSalePrice,
            BigDecimal estimatedProfit,
            long activeProjects,
            BigDecimal materialsRemainingWithSuppliers
    ) {
    }
}
