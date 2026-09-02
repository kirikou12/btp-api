package mr.btp.api.invoice;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import mr.btp.api.category.CategoryType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface SupplierInvoiceItemRepository extends JpaRepository<SupplierInvoiceItem, Long> {
    List<SupplierInvoiceItem> findByInvoiceId(Long invoiceId);
    List<SupplierInvoiceItem> findBySourceSupplyItemId(Long sourceSupplyItemId);
    long countByCategoryId(Long categoryId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<SupplierInvoiceItem> findByIdIn(List<Long> ids);

    @Query("""
            select item
            from SupplierInvoiceItem item
            join fetch item.invoice invoice
            join fetch item.category category
            left join fetch item.sourceSupplyItem sourceItem
            where invoice.id in :invoiceIds
            order by invoice.id asc, item.id asc
            """)
    List<SupplierInvoiceItem> findDetailedByInvoiceIdIn(@Param("invoiceIds") Collection<Long> invoiceIds);

    @Query("""
            select item.sourceSupplyItem.id as sourceSupplyItemId,
                   coalesce(sum(item.totalAmount), 0) as totalAmount,
                   coalesce(sum(item.quantity), 0) as totalQuantity
            from SupplierInvoiceItem item
            join item.invoice invoice
            where item.sourceSupplyItem.id in :sourceItemIds
              and invoice.invoiceType in :invoiceTypes
              and (:excludingInvoiceId is null or invoice.id <> :excludingInvoiceId)
            group by item.sourceSupplyItem.id
            """)
    List<SourceItemUsageTotal> sumOutgoingBySourceItemIds(@Param("sourceItemIds") Collection<Long> sourceItemIds,
                                                          @Param("invoiceTypes") Collection<InvoiceType> invoiceTypes,
                                                          @Param("excludingInvoiceId") Long excludingInvoiceId);

    @Query("""
            select item.invoice.id as invoiceId,
                   coalesce(sum(item.totalAmount), 0) as totalAmount
            from SupplierInvoiceItem item
            where item.invoice.id in :invoiceIds
            group by item.invoice.id
            """)
    List<InvoiceItemTotal> sumTotalsByInvoiceIds(@Param("invoiceIds") Collection<Long> invoiceIds);

    @Query("""
            select item.category.type as categoryType,
                   coalesce(sum(item.totalAmount), 0) as totalAmount
            from SupplierInvoiceItem item
            join item.invoice invoice
            where invoice.invoiceType in :invoiceTypes
              and (:projectId is null or invoice.project.id = :projectId)
              and (invoice.stage is null or invoice.stage.excludedFromProjectStats = false)
            group by item.category.type
            """)
    List<CategoryTypeTotal> sumUsageTotalsByCategoryType(@Param("projectId") Long projectId,
                                                         @Param("invoiceTypes") Collection<InvoiceType> invoiceTypes);

    @Query("""
            select invoice.project.id as projectId,
                   item.category.type as categoryType,
                   coalesce(sum(item.totalAmount), 0) as totalAmount
            from SupplierInvoiceItem item
            join item.invoice invoice
            where invoice.invoiceType in :invoiceTypes
              and invoice.project is not null
              and (invoice.stage is null or invoice.stage.excludedFromProjectStats = false)
            group by invoice.project.id, item.category.type
            """)
    List<ProjectCategoryTypeTotal> sumUsageTotalsByProjectAndCategoryType(@Param("invoiceTypes") Collection<InvoiceType> invoiceTypes);

    @Query("""
            select invoice.stage.id as stageId,
                   coalesce(sum(item.totalAmount), 0) as totalAmount
            from SupplierInvoiceItem item
            join item.invoice invoice
            where invoice.project.id = :projectId
              and invoice.stage is not null
              and invoice.stage.excludedFromProjectStats = false
              and invoice.invoiceType in :invoiceTypes
            group by invoice.stage.id
            """)
    List<StageCostTotal> sumUsageTotalsByStage(@Param("projectId") Long projectId,
                                               @Param("invoiceTypes") Collection<InvoiceType> invoiceTypes);

    @Query("""
            select item.category.id as categoryId,
                   item.category.name as categoryName,
                   coalesce(sum(item.totalAmount), 0) as totalAmount
            from SupplierInvoiceItem item
            join item.invoice invoice
            where invoice.project.id = :projectId
              and invoice.invoiceType in :invoiceTypes
              and (invoice.stage is null or invoice.stage.excludedFromProjectStats = false)
            group by item.category.id, item.category.name
            """)
    List<CategoryCostTotal> sumUsageTotalsByCategory(@Param("projectId") Long projectId,
                                                     @Param("invoiceTypes") Collection<InvoiceType> invoiceTypes);

    @Query("""
            select coalesce(sum(item.totalAmount), 0)
            from SupplierInvoiceItem item
            join item.invoice invoice
            where invoice.stage.id = :stageId
              and invoice.invoiceType in :invoiceTypes
            """)
    BigDecimal sumUsageTotalByStage(@Param("stageId") Long stageId,
                                    @Param("invoiceTypes") Collection<InvoiceType> invoiceTypes);

    interface SourceItemUsageTotal {
        Long getSourceSupplyItemId();
        BigDecimal getTotalAmount();
        BigDecimal getTotalQuantity();
    }

    interface InvoiceItemTotal {
        Long getInvoiceId();
        BigDecimal getTotalAmount();
    }

    interface CategoryTypeTotal {
        CategoryType getCategoryType();
        BigDecimal getTotalAmount();
    }

    interface ProjectCategoryTypeTotal {
        Long getProjectId();
        CategoryType getCategoryType();
        BigDecimal getTotalAmount();
    }

    interface StageCostTotal {
        Long getStageId();
        BigDecimal getTotalAmount();
    }

    interface CategoryCostTotal {
        Long getCategoryId();
        String getCategoryName();
        BigDecimal getTotalAmount();
    }
}
