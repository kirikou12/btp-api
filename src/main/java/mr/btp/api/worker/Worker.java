package mr.btp.api.worker;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import mr.btp.api.common.entity.BaseEntity;

@Entity
@Table(name = "workers")
public class Worker extends BaseEntity {

    @Column(nullable = false)
    private String name;

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
