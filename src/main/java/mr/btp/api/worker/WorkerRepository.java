package mr.btp.api.worker;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkerRepository extends JpaRepository<Worker, Long> {
    List<Worker> findAllByOrderByNameAsc();
    List<Worker> findByProjectIdOrderByNameAsc(Long projectId);
}
