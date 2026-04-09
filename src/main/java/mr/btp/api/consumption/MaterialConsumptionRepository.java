package mr.btp.api.consumption;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MaterialConsumptionRepository extends JpaRepository<MaterialConsumption, Long> {
    List<MaterialConsumption> findByProjectIdOrderByConsumptionDateDesc(Long projectId);
    List<MaterialConsumption> findByInvoiceItemId(Long invoiceItemId);
    long countByStageId(Long stageId);
}
