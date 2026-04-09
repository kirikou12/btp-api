package mr.btp.api.invoice;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SupplierInvoiceRepository extends JpaRepository<SupplierInvoice, Long> {
    List<SupplierInvoice> findByProjectIdOrProjectIdIsNullOrderByInvoiceDateDesc(Long projectId);
}
