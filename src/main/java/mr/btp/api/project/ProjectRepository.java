package mr.btp.api.project;

import java.math.BigDecimal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ProjectRepository extends JpaRepository<Project, Long> {

    @Query("""
            select coalesce(sum(project.budget), 0) as totalBudget,
                   coalesce(sum(project.estimatedSalePrice), 0) as estimatedSalePrice,
                   count(case when project.status = mr.btp.api.project.ProjectStatus.IN_PROGRESS then 1 end) as activeProjects
            from Project project
            """)
    ProjectTotals calculateTotals();

    interface ProjectTotals {
        BigDecimal getTotalBudget();
        BigDecimal getEstimatedSalePrice();
        long getActiveProjects();
    }
}
