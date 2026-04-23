package mr.btp.api.expense;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectExpenseEntryCategoryRepository extends JpaRepository<ProjectExpenseEntryCategory, String> {
    List<ProjectExpenseEntryCategory> findByEntryIdIn(Collection<String> entryIds);
}
