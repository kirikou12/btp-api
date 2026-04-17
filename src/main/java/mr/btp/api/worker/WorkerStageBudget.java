package mr.btp.api.worker;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import mr.btp.api.common.entity.BaseEntity;
import mr.btp.api.project.ConstructionStage;

@Entity
@Table(
        name = "worker_stage_budgets",
        uniqueConstraints = @UniqueConstraint(name = "uk_worker_stage_budget", columnNames = {"worker_id", "stage_id"})
)
public class WorkerStageBudget extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "worker_id", nullable = false)
    private Worker worker;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "stage_id", nullable = false)
    private ConstructionStage stage;

    @Column(name = "planned_budget", nullable = false, precision = 19, scale = 2)
    private BigDecimal plannedBudget;

    public Worker getWorker() {
        return worker;
    }

    public void setWorker(Worker worker) {
        this.worker = worker;
    }

    public ConstructionStage getStage() {
        return stage;
    }

    public void setStage(ConstructionStage stage) {
        this.stage = stage;
    }

    public BigDecimal getPlannedBudget() {
        return plannedBudget;
    }

    public void setPlannedBudget(BigDecimal plannedBudget) {
        this.plannedBudget = plannedBudget;
    }
}
