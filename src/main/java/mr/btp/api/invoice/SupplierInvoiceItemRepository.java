package mr.btp.api.invoice;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SupplierInvoiceItemRepository extends JpaRepository<SupplierInvoiceItem, Long> {
    List<SupplierInvoiceItem> findByInvoiceId(Long invoiceId);
}
