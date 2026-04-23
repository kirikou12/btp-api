package mr.btp.api.worker;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
            """)
    BigDecimal sumAmountByProjectId(@Param("projectId") Long projectId);

    @Query(value = """
            select payment
            from WorkerPayment payment
            join fetch payment.worker worker
            join fetch payment.stage stage
            join fetch stage.project project
            where project.id = :projectId
              and (:stageIdsEmpty = true or stage.id in :stageIds)
              and (:workerIdsEmpty = true or worker.id in :workerIds)
            order by payment.paymentDate desc, payment.id desc
            """,
            countQuery = """
            select count(payment)
            from WorkerPayment payment
            join payment.worker worker
            join payment.stage stage
            join stage.project project
            where project.id = :projectId
              and (:stageIdsEmpty = true or stage.id in :stageIds)
              and (:workerIdsEmpty = true or worker.id in :workerIds)
            """)
    Page<WorkerPayment> findProjectExpensePayments(@Param("projectId") Long projectId,
                                                   @Param("stageIdsEmpty") boolean stageIdsEmpty,
                                                   @Param("stageIds") List<Long> stageIds,
                                                   @Param("workerIdsEmpty") boolean workerIdsEmpty,
                                                   @Param("workerIds") List<Long> workerIds,
                                                   Pageable pageable);

    @Query("""
            select payment.stage.project.id as projectId,
                   coalesce(sum(payment.amount), 0) as totalAmount
            from WorkerPayment payment
            group by payment.stage.project.id
            """)
    List<ProjectWorkerPaymentTotal> sumAmountsByProject();

    @Query("""
            select payment.stage.id as stageId,
                   coalesce(sum(payment.amount), 0) as totalAmount
            from WorkerPayment payment
            where payment.stage.project.id = :projectId
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
            group by payment.worker.id
            """)
    List<WorkerPaymentTotal> sumAmountsByWorkerIds(@Param("workerIds") Collection<Long> workerIds);

    @Query("""
            select payment.worker.type as workerType,
                   coalesce(sum(payment.amount), 0) as totalAmount
            from WorkerPayment payment
            where payment.stage.project.id = :projectId
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
