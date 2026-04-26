package mr.btp.api.invoice;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class InvoiceDtos {

    private InvoiceDtos() {
    }

    public record InvoiceRequest(
            InvoiceType invoiceType,
            Long supplierId,
            Long projectId,
            Long stageId,
            Long sourceSupplyInvoiceId,
            String reference,
            @NotNull(message = "{validation.invoice.date.required}") LocalDate invoiceDate,
            BigDecimal totalAmount,
            String currency,
            String notes,
            String documentRef,
            List<String> documentUrls,
            @NotNull(message = "{validation.invoice.status.required}") InvoiceStatus status,
            @Valid @NotEmpty(message = "{validation.invoice.items.required}") List<InvoiceItemUpsertRequest> items
    ) {
        public InvoiceRequest(InvoiceType invoiceType,
                              Long supplierId,
                              Long projectId,
                              Long stageId,
                              Long sourceSupplyInvoiceId,
                              String reference,
                              @NotNull(message = "{validation.invoice.date.required}") LocalDate invoiceDate,
                              BigDecimal totalAmount,
                              String currency,
                              String notes,
                              String documentRef,
                              @NotNull(message = "{validation.invoice.status.required}") InvoiceStatus status,
                              @Valid @NotEmpty(message = "{validation.invoice.items.required}") List<InvoiceItemUpsertRequest> items) {
            this(invoiceType, supplierId, projectId, stageId, sourceSupplyInvoiceId, reference, invoiceDate, totalAmount, currency, notes, documentRef, null, status, items);
        }
    }

    public record InvoiceResponse(
            Long id,
            InvoiceType invoiceType,
            Long supplierId,
            Long projectId,
            Long stageId,
            String stageName,
            Long sourceSupplyInvoiceId,
            String sourceSupplyReference,
            String supplierName,
            String reference,
            LocalDate invoiceDate,
            BigDecimal totalAmount,
            String currency,
            String notes,
            String documentRef,
            List<String> documentUrls,
            String status,
            BigDecimal consumedAmount,
            BigDecimal remainingAmount,
            List<InvoiceItemResponse> items
    ) {
    }

    public record InvoiceItemUpsertRequest(
            Long invoiceId,
            Long sourceSupplyItemId,
            Long categoryId,
            String description,
            BigDecimal quantity,
            String unit,
            BigDecimal unitPrice,
            BigDecimal totalAmount,
            Long id
    ) {
    }

    public record InvoiceItemResponse(
            Long id,
            Long invoiceId,
            Long sourceSupplyItemId,
            Long categoryId,
            String categoryName,
            String description,
            BigDecimal quantity,
            String unit,
            BigDecimal unitPrice,
            BigDecimal totalAmount,
            BigDecimal consumedAmount,
            BigDecimal remainingAmount,
            BigDecimal availableQuantity
    ) {
    }

    public record UsageInvoiceRequest(
            @NotNull(message = "{validation.usage-invoice.source-supply-invoice.required}") Long sourceSupplyInvoiceId,
            @NotNull(message = "{validation.usage-invoice.project.required}") Long projectId,
            @NotNull(message = "{validation.usage-invoice.stage.required}") Long stageId,
            @NotNull(message = "{validation.invoice.date.required}") LocalDate invoiceDate,
            String notes,
            String documentRef,
            List<String> documentUrls,
            @NotNull(message = "{validation.invoice.status.required}") InvoiceStatus status,
            @Valid @NotEmpty(message = "{validation.invoice.items.required}") List<UsageInvoiceItemRequest> items
    ) {
        public UsageInvoiceRequest(@NotNull(message = "{validation.usage-invoice.source-supply-invoice.required}") Long sourceSupplyInvoiceId,
                                   @NotNull(message = "{validation.usage-invoice.project.required}") Long projectId,
                                   @NotNull(message = "{validation.usage-invoice.stage.required}") Long stageId,
                                   @NotNull(message = "{validation.invoice.date.required}") LocalDate invoiceDate,
                                   String notes,
                                   String documentRef,
                                   @NotNull(message = "{validation.invoice.status.required}") InvoiceStatus status,
                                   @Valid @NotEmpty(message = "{validation.invoice.items.required}") List<UsageInvoiceItemRequest> items) {
            this(sourceSupplyInvoiceId, projectId, stageId, invoiceDate, notes, documentRef, null, status, items);
        }
    }

    public record UsageInvoiceItemRequest(
            @NotNull(message = "{validation.usage-invoice.source-item.required}") Long sourceSupplyItemId,
            @NotNull(message = "{validation.usage-invoice.quantity.required}") @DecimalMin(value = "0.00", message = "{validation.usage-invoice.quantity.non-negative}") BigDecimal quantityUsed
    ) {
    }
}
