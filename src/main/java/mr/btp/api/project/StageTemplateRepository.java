package mr.btp.api.project;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StageTemplateRepository extends JpaRepository<StageTemplate, Long> {
    List<StageTemplate> findAllByOrderBySortOrderAsc();
    List<StageTemplate> findByActiveTrueOrderBySortOrderAsc();
    boolean existsByNameIgnoreCase(String name);
}
