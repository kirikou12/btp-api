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
            @NotNull Long supplierId,
            Long projectId,
            String reference,
            @NotNull LocalDate invoiceDate,
            @NotNull @DecimalMin("0.00") BigDecimal totalAmount,
            @NotBlank String currency,
            String notes,
            @NotNull InvoiceStatus status,
            @Valid @NotEmpty List<InvoiceItemUpsertRequest> items
    ) {
    }

    public record InvoiceResponse(
            Long id,
            Long supplierId,
            Long projectId,
            String supplierName,
            String reference,
            LocalDate invoiceDate,
            BigDecimal totalAmount,
            String currency,
            String notes,
            String status,
            BigDecimal consumedAmount,
            BigDecimal remainingAmount,
            List<InvoiceItemResponse> items
    ) {
    }

    public record InvoiceItemUpsertRequest(
            Long invoiceId,
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
            Long categoryId,
            String categoryName,
            String description,
            BigDecimal quantity,
            String unit,
            BigDecimal unitPrice,
            BigDecimal totalAmount,
            BigDecimal consumedAmount,
            BigDecimal remainingAmount
    ) {
    }
}
