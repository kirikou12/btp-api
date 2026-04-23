package mr.btp.api.expense;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import mr.btp.api.invoice.InvoiceType;
import mr.btp.api.worker.WorkerType;
import org.hibernate.annotations.Immutable;

@Entity
@Immutable
@Table(name = "project_expense_entries")
public class ProjectExpenseEntry {

    @Id
    private String id;

    private String sourceKind;

    private Long sourceId;

    private Long projectId;

    private Long stageId;

    private String stageName;

    private Long supplierId;

    private String supplierName;

    private Long workerId;

    private String workerName;

    @Enumerated(EnumType.STRING)
    private WorkerType workerType;

    @Enumerated(EnumType.STRING)
    private InvoiceType invoiceType;

    private LocalDate expenseDate;

    @Column(precision = 19, scale = 2)
    private BigDecimal amount;

    private String category;

    private String description;

    public String getId() {
        return id;
    }

    public String getSourceKind() {
        return sourceKind;
    }

    public Long getSourceId() {
        return sourceId;
    }

    public Long getProjectId() {
        return projectId;
    }

    public Long getStageId() {
        return stageId;
    }

    public String getStageName() {
        return stageName;
    }

    public Long getSupplierId() {
        return supplierId;
    }

    public String getSupplierName() {
        return supplierName;
    }

    public Long getWorkerId() {
        return workerId;
    }

    public String getWorkerName() {
        return workerName;
    }

    public WorkerType getWorkerType() {
        return workerType;
    }

    public InvoiceType getInvoiceType() {
        return invoiceType;
    }

    public LocalDate getExpenseDate() {
        return expenseDate;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCategory() {
        return category;
    }

    public String getDescription() {
        return description;
    }
}
