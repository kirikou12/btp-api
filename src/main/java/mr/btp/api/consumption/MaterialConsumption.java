package mr.btp.api.consumption;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import mr.btp.api.category.ExpenseCategory;
import mr.btp.api.common.entity.BaseEntity;
import mr.btp.api.invoice.SupplierInvoiceItem;
import mr.btp.api.project.ConstructionStage;
import mr.btp.api.project.Project;

@Entity
@Table(name = "material_consumptions")
public class MaterialConsumption extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invoice_item_id", nullable = false)
    private SupplierInvoiceItem invoiceItem;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "stage_id", nullable = false)
    private ConstructionStage stage;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private ExpenseCategory category;

    @Column(name = "quantity_used", precision = 19, scale = 2)
    private BigDecimal quantityUsed;

    @Column(name = "amount_used", nullable = false, precision = 19, scale = 2)
    private BigDecimal amountUsed;

    @Column(name = "consumption_date", nullable = false)
    private LocalDate consumptionDate;

    @Column(columnDefinition = "text")
    private String notes;

    public SupplierInvoiceItem getInvoiceItem() {
        return invoiceItem;
    }

    public void setInvoiceItem(SupplierInvoiceItem invoiceItem) {
        this.invoiceItem = invoiceItem;
    }

    public Project getProject() {
        return project;
    }

    public void setProject(Project project) {
        this.project = project;
    }

    public ConstructionStage getStage() {
        return stage;
    }

    public void setStage(ConstructionStage stage) {
        this.stage = stage;
    }

    public ExpenseCategory getCategory() {
        return category;
    }

    public void setCategory(ExpenseCategory category) {
        this.category = category;
    }

    public BigDecimal getQuantityUsed() {
        return quantityUsed;
    }

    public void setQuantityUsed(BigDecimal quantityUsed) {
        this.quantityUsed = quantityUsed;
    }

    public BigDecimal getAmountUsed() {
        return amountUsed;
    }

    public void setAmountUsed(BigDecimal amountUsed) {
        this.amountUsed = amountUsed;
    }

    public LocalDate getConsumptionDate() {
        return consumptionDate;
    }

    public void setConsumptionDate(LocalDate consumptionDate) {
        this.consumptionDate = consumptionDate;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }
}
