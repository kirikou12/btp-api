package mr.btp.api.worker;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinTable;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.Set;
import mr.btp.api.common.entity.BaseEntity;
import mr.btp.api.project.Project;

@Entity
@Table(name = "workers")
public class Worker extends BaseEntity {

    @Column(nullable = false)
    private String name;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "worker_project_assignments",
            joinColumns = @JoinColumn(name = "worker_id"),
            inverseJoinColumns = @JoinColumn(name = "project_id")
    )
    private Set<Project> projects = new LinkedHashSet<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private WorkerType type;

    @Column(name = "planned_budget", nullable = false, precision = 19, scale = 2)
    private BigDecimal plannedBudget;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Set<Project> getProjects() {
        return projects;
    }

    public void setProjects(Set<Project> projects) {
        this.projects = projects;
    }

    public WorkerType getType() {
        return type;
    }

    public void setType(WorkerType type) {
        this.type = type;
    }

    public BigDecimal getPlannedBudget() {
        return plannedBudget;
    }

    public void setPlannedBudget(BigDecimal plannedBudget) {
        this.plannedBudget = plannedBudget;
    }
}
