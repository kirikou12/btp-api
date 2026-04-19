package mr.btp.api.worker;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkerStageBudgetRepository extends JpaRepository<WorkerStageBudget, Long> {
    List<WorkerStageBudget> findByWorker_IdOrderByStage_SortOrderAsc(Long workerId);

    void deleteByWorker_Id(Long workerId);

    void deleteByWorker_IdAndStage_Project_Id(Long workerId, Long projectId);
}
