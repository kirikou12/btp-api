package mr.btp.api.expense;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProjectExpenseEntryRepository extends JpaRepository<ProjectExpenseEntry, String> {

    @Query(value = """
            select entry
            from ProjectExpenseEntry entry
            where entry.projectId = :projectId
              and (:stageIdsEmpty = true or entry.stageId in :stageIds)
              and (:workerIdsEmpty = true or entry.workerId in :workerIds)
              and (:supplierIdsEmpty = true or entry.supplierId in :supplierIds)
              and (
                  :categoryIdsEmpty = true
                  or exists (
                      select category.id
                      from ProjectExpenseEntryCategory category
                      where category.entryId = entry.id
                        and category.categoryId in :categoryIds
                  )
              )
            order by entry.expenseDate desc, entry.sourceId desc
            """,
            countQuery = """
            select count(entry)
            from ProjectExpenseEntry entry
            where entry.projectId = :projectId
              and (:stageIdsEmpty = true or entry.stageId in :stageIds)
              and (:workerIdsEmpty = true or entry.workerId in :workerIds)
              and (:supplierIdsEmpty = true or entry.supplierId in :supplierIds)
              and (
                  :categoryIdsEmpty = true
                  or exists (
                      select category.id
                      from ProjectExpenseEntryCategory category
                      where category.entryId = entry.id
                        and category.categoryId in :categoryIds
                  )
              )
            """)
    Page<ProjectExpenseEntry> search(@Param("projectId") Long projectId,
                                     @Param("stageIdsEmpty") boolean stageIdsEmpty,
                                     @Param("stageIds") List<Long> stageIds,
                                     @Param("workerIdsEmpty") boolean workerIdsEmpty,
                                     @Param("workerIds") List<Long> workerIds,
                                     @Param("supplierIdsEmpty") boolean supplierIdsEmpty,
                                     @Param("supplierIds") List<Long> supplierIds,
                                     @Param("categoryIdsEmpty") boolean categoryIdsEmpty,
                                     @Param("categoryIds") List<Long> categoryIds,
                                     Pageable pageable);
}
