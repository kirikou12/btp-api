package mr.btp.api.invoice;

import java.util.List;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import jakarta.persistence.LockModeType;

public interface SupplierInvoiceItemRepository extends JpaRepository<SupplierInvoiceItem, Long> {
    List<SupplierInvoiceItem> findByInvoiceId(Long invoiceId);
    List<SupplierInvoiceItem> findBySourceSupplyItemId(Long sourceSupplyItemId);
    long countByCategoryId(Long categoryId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<SupplierInvoiceItem> findByIdIn(List<Long> ids);
}
