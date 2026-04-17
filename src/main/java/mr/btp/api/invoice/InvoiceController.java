package mr.btp.api.invoice;

import jakarta.validation.Valid;
import mr.btp.api.common.dto.PageResponse;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class InvoiceController {

    private final InvoiceService invoiceService;

    public InvoiceController(InvoiceService invoiceService) {
        this.invoiceService = invoiceService;
    }

    @GetMapping("/api/supplier-invoices")
    public PageResponse<InvoiceDtos.InvoiceResponse> list(@RequestParam(defaultValue = "0") int page,
                                                          @RequestParam(defaultValue = "20") int size,
                                                          @RequestParam(required = false) String type) {
        return invoiceService.list(page, size, type);
    }

    @PostMapping("/api/supplier-invoices")
    public InvoiceDtos.InvoiceResponse create(@Valid @RequestBody InvoiceDtos.InvoiceRequest request) {
        return invoiceService.create(request);
    }

    @GetMapping("/api/supplier-invoices/{id}")
    public InvoiceDtos.InvoiceResponse get(@PathVariable Long id) {
        return invoiceService.get(id);
    }

    @PutMapping("/api/supplier-invoices/{id}")
    public InvoiceDtos.InvoiceResponse update(@PathVariable Long id, @Valid @RequestBody InvoiceDtos.InvoiceRequest request) {
        return invoiceService.update(id, request);
    }

    @PostMapping("/api/usage-invoices")
    public InvoiceDtos.InvoiceResponse createUsage(@Valid @RequestBody InvoiceDtos.UsageInvoiceRequest request) {
        return invoiceService.createUsage(request);
    }

    @PutMapping("/api/usage-invoices/{id}")
    public InvoiceDtos.InvoiceResponse updateUsage(@PathVariable Long id, @Valid @RequestBody InvoiceDtos.UsageInvoiceRequest request) {
        return invoiceService.updateUsage(id, request);
    }

    @DeleteMapping("/api/usage-invoices/{id}")
    public void deleteUsage(@PathVariable Long id) {
        invoiceService.deleteUsage(id);
    }

    @PostMapping("/api/supplier-invoice-items")
    public InvoiceDtos.InvoiceItemResponse createItem(@Valid @RequestBody InvoiceDtos.InvoiceItemUpsertRequest request) {
        return invoiceService.createItem(request);
    }

    @PutMapping("/api/supplier-invoice-items/{id}")
    public InvoiceDtos.InvoiceItemResponse updateItem(@PathVariable Long id,
                                                      @Valid @RequestBody InvoiceDtos.InvoiceItemUpsertRequest request) {
        return invoiceService.updateItem(id, request);
    }
}
