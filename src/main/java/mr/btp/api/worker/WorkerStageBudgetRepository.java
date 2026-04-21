package mr.btp.api.worker;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WorkerStageBudgetRepository extends JpaRepository<WorkerStageBudget, Long> {
    List<WorkerStageBudget> findByWorker_IdOrderByStage_SortOrderAsc(Long workerId);

    @Query("""
            select budget
            from WorkerStageBudget budget
            join fetch budget.stage stage
            where budget.worker.id in :workerIds
            order by budget.worker.id asc, stage.sortOrder asc
            """)
    List<WorkerStageBudget> findDetailedByWorkerIds(@Param("workerIds") Collection<Long> workerIds);

    void deleteByWorker_Id(Long workerId);

    void deleteByWorker_IdAndStage_Project_Id(Long workerId, Long projectId);

    long countByStageId(Long stageId);
}
