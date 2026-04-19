package mr.btp.api.dashboard;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/dashboard")
    public DashboardDtos.DashboardResponse global() {
        return dashboardService.global();
    }

    @GetMapping("/projects/{id}/dashboard")
    public DashboardDtos.DashboardResponse byProject(@PathVariable Long id) {
        return dashboardService.byProject(id);
    }

    @GetMapping("/projects/expense-summaries")
    public List<DashboardDtos.ProjectExpenseSummaryResponse> projectExpenseSummaries() {
        return dashboardService.projectExpenseSummaries();
    }
}
