package mr.btp.api.worker;

import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WorkerRepository extends JpaRepository<Worker, Long> {
    @EntityGraph(attributePaths = "projects")
    List<Worker> findAllByOrderByNameAsc();

    @EntityGraph(attributePaths = "projects")
    @Query("""
            select distinct worker
            from Worker worker
            join worker.projects project
            where project.id = :projectId
            order by worker.name asc
            """)
    List<Worker> findByProjectMembershipOrderByNameAsc(@Param("projectId") Long projectId);
}
