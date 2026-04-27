package mr.btp.api.invoice;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import mr.btp.api.common.dto.PageResponse;
import mr.btp.api.common.exception.ApiException;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class InvoiceController {

    private final InvoiceService invoiceService;
    private final ObjectMapper objectMapper;
    private final Validator validator;

    public InvoiceController(InvoiceService invoiceService, ObjectMapper objectMapper, Validator validator) {
        this.invoiceService = invoiceService;
        this.objectMapper = objectMapper;
        this.validator = validator;
    }

    @GetMapping("/api/supplier-invoices")
    public PageResponse<InvoiceDtos.InvoiceResponse> list(@RequestParam(defaultValue = "0") int page,
                                                          @RequestParam(defaultValue = "20") int size,
                                                          @RequestParam(required = false) String type,
                                                          @RequestParam(required = false) Long projectId) {
        return invoiceService.list(page, size, type, projectId);
    }

    @PostMapping(value = "/api/supplier-invoices", consumes = MediaType.APPLICATION_JSON_VALUE)
    public InvoiceDtos.InvoiceResponse create(@Valid @RequestBody InvoiceDtos.InvoiceRequest request) {
        return invoiceService.create(request);
    }

    @PostMapping(value = "/api/supplier-invoices", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public InvoiceDtos.InvoiceResponse createWithDocuments(@RequestPart("payload") String payload,
                                                           @RequestPart(value = "documents", required = false) List<MultipartFile> documents) {
        return invoiceService.create(readPayload(payload, InvoiceDtos.InvoiceRequest.class), documents);
    }

    @GetMapping("/api/supplier-invoices/{id}")
    public InvoiceDtos.InvoiceResponse get(@PathVariable Long id) {
        return invoiceService.get(id);
    }

    @PutMapping(value = "/api/supplier-invoices/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public InvoiceDtos.InvoiceResponse update(@PathVariable Long id, @Valid @RequestBody InvoiceDtos.InvoiceRequest request) {
        return invoiceService.update(id, request);
    }

    @PutMapping(value = "/api/supplier-invoices/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public InvoiceDtos.InvoiceResponse updateWithDocuments(@PathVariable Long id,
                                                           @RequestPart("payload") String payload,
                                                           @RequestPart(value = "documents", required = false) List<MultipartFile> documents) {
        return invoiceService.update(id, readPayload(payload, InvoiceDtos.InvoiceRequest.class), documents);
    }

    @DeleteMapping("/api/supplier-invoices/{id}")
    public void delete(@PathVariable Long id) {
        invoiceService.delete(id);
    }

    @PostMapping(value = "/api/usage-invoices", consumes = MediaType.APPLICATION_JSON_VALUE)
    public InvoiceDtos.InvoiceResponse createUsage(@Valid @RequestBody InvoiceDtos.UsageInvoiceRequest request) {
        return invoiceService.createUsage(request);
    }

    @PostMapping(value = "/api/usage-invoices", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public InvoiceDtos.InvoiceResponse createUsageWithDocuments(@RequestPart("payload") String payload,
                                                                @RequestPart(value = "documents", required = false) List<MultipartFile> documents) {
        return invoiceService.createUsage(readPayload(payload, InvoiceDtos.UsageInvoiceRequest.class), documents);
    }

    @PutMapping(value = "/api/usage-invoices/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public InvoiceDtos.InvoiceResponse updateUsage(@PathVariable Long id, @Valid @RequestBody InvoiceDtos.UsageInvoiceRequest request) {
        return invoiceService.updateUsage(id, request);
    }

    @PutMapping(value = "/api/usage-invoices/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public InvoiceDtos.InvoiceResponse updateUsageWithDocuments(@PathVariable Long id,
                                                                @RequestPart("payload") String payload,
                                                                @RequestPart(value = "documents", required = false) List<MultipartFile> documents) {
        return invoiceService.updateUsage(id, readPayload(payload, InvoiceDtos.UsageInvoiceRequest.class), documents);
    }

    @DeleteMapping("/api/usage-invoices/{id}")
    public void deleteUsage(@PathVariable Long id) {
        invoiceService.deleteUsage(id);
    }

    @DeleteMapping("/api/return-invoices/{id}")
    public void deleteReturn(@PathVariable Long id) {
        invoiceService.deleteReturn(id);
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

    private <T> T readPayload(String payload, Class<T> type) {
        try {
            T request = objectMapper.readValue(payload, type);
            var violations = validator.validate(request);
            if (!violations.isEmpty()) {
                throw new ConstraintViolationException(violations);
            }
            return request;
        } catch (JsonProcessingException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "error.request.invalid-json", "Invalid request payload");
        }
    }
}
