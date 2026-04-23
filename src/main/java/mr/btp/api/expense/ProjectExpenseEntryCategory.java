package mr.btp.api.expense;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

@Entity
@Immutable
@Table(name = "project_expense_entry_categories")
public class ProjectExpenseEntryCategory {

    @Id
    private String id;

    private String entryId;

    private Long categoryId;

    public String getId() {
        return id;
    }

    public String getEntryId() {
        return entryId;
    }

    public Long getCategoryId() {
        return categoryId;
    }
}
