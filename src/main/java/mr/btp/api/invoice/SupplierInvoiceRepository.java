package mr.btp.api.invoice;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SupplierInvoiceRepository extends JpaRepository<SupplierInvoice, Long> {
    List<SupplierInvoice> findByProjectIdOrProjectIdIsNullOrderByInvoiceDateDesc(Long projectId);
    List<SupplierInvoice> findByInvoiceTypeOrderByInvoiceDateDesc(InvoiceType invoiceType);
    List<SupplierInvoice> findByProjectIdAndInvoiceTypeOrderByInvoiceDateDesc(Long projectId, InvoiceType invoiceType);
    List<SupplierInvoice> findByProjectIdAndInvoiceTypeOrderByInvoiceDateDescIdDesc(Long projectId, InvoiceType invoiceType);
    List<SupplierInvoice> findByProjectIdAndStageIdAndInvoiceTypeOrderByInvoiceDateDescIdDesc(Long projectId, Long stageId, InvoiceType invoiceType);
}
