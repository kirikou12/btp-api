package mr.btp.api.invoice;

import java.math.BigDecimal;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SupplierInvoiceRepository extends JpaRepository<SupplierInvoice, Long> {
    List<SupplierInvoice> findByProjectIdOrProjectIdIsNullOrderByInvoiceDateDesc(Long projectId);
    List<SupplierInvoice> findByInvoiceTypeOrderByInvoiceDateDesc(InvoiceType invoiceType);
    List<SupplierInvoice> findByProjectIdAndInvoiceTypeOrderByInvoiceDateDesc(Long projectId, InvoiceType invoiceType);
    List<SupplierInvoice> findByProjectIdAndInvoiceTypeOrderByInvoiceDateDescIdDesc(Long projectId, InvoiceType invoiceType);
    List<SupplierInvoice> findByProjectIdAndStageIdAndInvoiceTypeOrderByInvoiceDateDescIdDesc(Long projectId, Long stageId, InvoiceType invoiceType);
    List<SupplierInvoice> findByProjectIdAndInvoiceTypeInOrderByInvoiceDateDescIdDesc(Long projectId, List<InvoiceType> invoiceTypes);
    List<SupplierInvoice> findByProjectIdAndStageIdAndInvoiceTypeInOrderByInvoiceDateDescIdDesc(Long projectId, Long stageId, List<InvoiceType> invoiceTypes);
    long countByStageId(Long stageId);
    long countBySupplier_Id(Long supplierId);

    @Query("""
            select coalesce(sum(invoice.totalAmount), 0)
            from SupplierInvoice invoice
            where invoice.sourceSupplyInvoice.id = :sourceInvoiceId
              and invoice.invoiceType = mr.btp.api.invoice.InvoiceType.SUPPLY_EXCHANGE
              and (:excludingInvoiceId is null or invoice.id <> :excludingInvoiceId)
            """)
    BigDecimal sumExchangeTotalBySourceInvoiceId(@Param("sourceInvoiceId") Long sourceInvoiceId,
                                                 @Param("excludingInvoiceId") Long excludingInvoiceId);

    @Query(value = """
            select invoice
            from SupplierInvoice invoice
            left join fetch invoice.supplier supplier
            left join fetch invoice.project project
            left join fetch invoice.stage stage
            left join fetch invoice.sourceSupplyInvoice source
            where (:projectId is null or invoice.project.id = :projectId or source.project.id = :projectId)
              and (:invoiceType is null or invoice.invoiceType = :invoiceType)
            order by invoice.invoiceDate desc, invoice.id desc
            """,
            countQuery = """
            select count(invoice)
            from SupplierInvoice invoice
            left join invoice.sourceSupplyInvoice source
            where (:projectId is null or invoice.project.id = :projectId or source.project.id = :projectId)
              and (:invoiceType is null or invoice.invoiceType = :invoiceType)
            """)
    Page<SupplierInvoice> findForProjectAndType(@Param("projectId") Long projectId,
                                                @Param("invoiceType") InvoiceType invoiceType,
                                                Pageable pageable);

    @Query("""
            select invoice
            from SupplierInvoice invoice
            join fetch invoice.supplier supplier
            left join fetch invoice.project project
            where invoice.project.id = :projectId
              and invoice.invoiceType = mr.btp.api.invoice.InvoiceType.SUPPLY
            order by invoice.invoiceDate desc, invoice.id desc
            """)
    List<SupplierInvoice> findSupplyInvoicesForProject(@Param("projectId") Long projectId);

    @Query("""
            select invoice
            from SupplierInvoice invoice
            left join fetch invoice.supplier supplier
            left join fetch invoice.project project
            left join fetch invoice.stage stage
            left join fetch invoice.sourceSupplyInvoice source
            where invoice.project.id = :projectId
              and invoice.invoiceType in :invoiceTypes
            order by invoice.invoiceDate desc, invoice.id desc
            """)
    List<SupplierInvoice> findDetailedByProjectIdAndInvoiceTypeIn(@Param("projectId") Long projectId,
                                                                  @Param("invoiceTypes") List<InvoiceType> invoiceTypes);
}
