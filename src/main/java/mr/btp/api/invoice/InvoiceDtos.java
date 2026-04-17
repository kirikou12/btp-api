package mr.btp.api.invoice;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
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
            @NotNull Long supplierId,
            Long projectId,
            String reference,
            @NotNull LocalDate invoiceDate,
            @NotNull @DecimalMin("0.00") BigDecimal totalAmount,
            @NotBlank String currency,
            String notes,
            String documentRef,
            @NotNull InvoiceStatus status,
            @Valid @NotEmpty List<InvoiceItemUpsertRequest> items
    ) {
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
            String status,
            BigDecimal consumedAmount,
            BigDecimal remainingAmount,
            List<InvoiceItemResponse> items
    ) {
    }

    public record InvoiceItemUpsertRequest(
            Long invoiceId,
            Long sourceSupplyItemId,
            @NotNull Long categoryId,
            @NotBlank String description,
            BigDecimal quantity,
            String unit,
            BigDecimal unitPrice,
            @NotNull @DecimalMin("0.00") BigDecimal totalAmount
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
            @NotNull Long sourceSupplyInvoiceId,
            @NotNull Long projectId,
            @NotNull Long stageId,
            @NotNull LocalDate invoiceDate,
            String notes,
            String documentRef,
            @NotNull InvoiceStatus status,
            @Valid @NotEmpty List<UsageInvoiceItemRequest> items
    ) {
    }

    public record UsageInvoiceItemRequest(
            @NotNull Long sourceSupplyItemId,
            @NotNull @DecimalMin("0.00") BigDecimal quantityUsed
    ) {
    }
}
