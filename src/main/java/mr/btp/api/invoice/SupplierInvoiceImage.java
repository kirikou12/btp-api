package mr.btp.api.invoice;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import mr.btp.api.common.entity.BaseEntity;

@Entity
@Table(name = "supplier_invoice_images")
public class SupplierInvoiceImage extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invoice_id", nullable = false)
    private SupplierInvoice invoice;

    @Column(name = "image_url", nullable = false, columnDefinition = "text")
    private String imageUrl;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    public SupplierInvoice getInvoice() {
        return invoice;
    }

    public void setInvoice(SupplierInvoice invoice) {
        this.invoice = invoice;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }
}
