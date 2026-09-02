package mr.btp.api.worker;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WorkerPaymentRepository extends JpaRepository<WorkerPayment, Long> {
    List<WorkerPayment> findByStage_Project_IdOrderByPaymentDateDesc(Long projectId);
    List<WorkerPayment> findByWorker_IdOrderByPaymentDateDesc(Long workerId);
    List<WorkerPayment> findByStage_Id(Long stageId);
    long countByStage_Id(Long stageId);

    @Query("""
            select coalesce(sum(payment.amount), 0)
            from WorkerPayment payment
            where (:projectId is null or payment.stage.project.id = :projectId)
              and payment.stage.excludedFromProjectStats = false
            """)
    BigDecimal sumAmountByProjectId(@Param("projectId") Long projectId);

    @Query("""
            select payment.stage.project.id as projectId,
                   coalesce(sum(payment.amount), 0) as totalAmount
            from WorkerPayment payment
            where payment.stage.excludedFromProjectStats = false
            group by payment.stage.project.id
            """)
    List<ProjectWorkerPaymentTotal> sumAmountsByProject();

    @Query("""
            select payment.stage.id as stageId,
                   coalesce(sum(payment.amount), 0) as totalAmount
            from WorkerPayment payment
            where payment.stage.project.id = :projectId
              and payment.stage.excludedFromProjectStats = false
            group by payment.stage.id
            """)
    List<StageWorkerPaymentTotal> sumAmountsByStageForProject(@Param("projectId") Long projectId);

    @Query("""
            select coalesce(sum(payment.amount), 0)
            from WorkerPayment payment
            where payment.stage.id = :stageId
            """)
    BigDecimal sumAmountByStageId(@Param("stageId") Long stageId);

    @Query("""
            select payment.worker.id as workerId,
                   coalesce(sum(payment.amount), 0) as totalAmount
            from WorkerPayment payment
            where payment.worker.id in :workerIds
              and payment.stage.excludedFromProjectStats = false
            group by payment.worker.id
            """)
    List<WorkerPaymentTotal> sumAmountsByWorkerIds(@Param("workerIds") Collection<Long> workerIds);

    @Query("""
            select payment.worker.type as workerType,
                   coalesce(sum(payment.amount), 0) as totalAmount
            from WorkerPayment payment
            where payment.stage.project.id = :projectId
              and payment.stage.excludedFromProjectStats = false
            group by payment.worker.type
            """)
    List<WorkerTypePaymentTotal> sumAmountsByWorkerTypeForProject(@Param("projectId") Long projectId);

    interface ProjectWorkerPaymentTotal {
        Long getProjectId();
        BigDecimal getTotalAmount();
    }

    interface StageWorkerPaymentTotal {
        Long getStageId();
        BigDecimal getTotalAmount();
    }

    interface WorkerPaymentTotal {
        Long getWorkerId();
        BigDecimal getTotalAmount();
    }

    interface WorkerTypePaymentTotal {
        WorkerType getWorkerType();
        BigDecimal getTotalAmount();
    }
}
