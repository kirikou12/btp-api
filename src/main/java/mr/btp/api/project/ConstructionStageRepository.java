package mr.btp.api.project;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConstructionStageRepository extends JpaRepository<ConstructionStage, Long> {
    List<ConstructionStage> findByProjectIdOrderBySortOrderAsc(Long projectId);
    List<ConstructionStage> findByStageTemplateId(Long stageTemplateId);
}
