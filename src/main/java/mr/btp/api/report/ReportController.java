package mr.btp.api.report;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping("/stage-costs")
    public List<ReportDtos.StageCostRow> stageCosts(@RequestParam Long projectId) {
        return reportService.stageCosts(projectId);
    }

    @GetMapping("/category-costs")
    public List<ReportDtos.CategoryCostRow> categoryCosts(@RequestParam Long projectId) {
        return reportService.categoryCosts(projectId);
    }

    @GetMapping("/supplier-balances")
    public List<ReportDtos.SupplierBalanceRow> supplierBalances(@RequestParam Long projectId) {
        return reportService.supplierBalances(projectId);
    }

    @GetMapping("/activity-feed")
    public List<ReportDtos.ActivityFeedRow> activityFeed(@RequestParam Long projectId) {
        return reportService.activityFeed(projectId);
    }
}
