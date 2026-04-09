package mr.btp.api.expense;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DirectExpenseRepository extends JpaRepository<DirectExpense, Long> {
    List<DirectExpense> findByProjectIdOrderByExpenseDateDesc(Long projectId);
}
