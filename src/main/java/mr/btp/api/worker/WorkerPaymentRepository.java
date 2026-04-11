package mr.btp.api.worker;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkerPaymentRepository extends JpaRepository<WorkerPayment, Long> {
    List<WorkerPayment> findByStage_Project_IdOrderByPaymentDateDesc(Long projectId);
    List<WorkerPayment> findByWorker_IdOrderByPaymentDateDesc(Long workerId);
    List<WorkerPayment> findByStage_Id(Long stageId);
    long countByStage_Id(Long stageId);
}
