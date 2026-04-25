package mr.btp.api.project;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConstructionStageRepository extends JpaRepository<ConstructionStage, Long> {
    List<ConstructionStage> findByProjectIdOrderBySortOrderAsc(Long projectId);
    Optional<ConstructionStage> findByProjectIdAndNameIgnoreCase(Long projectId, String name);
    long countByProjectIdAndStatus(Long projectId, StageStatus status);
}
