package mr.btp.api.invoice;

import mr.btp.api.category.CategoryType;
import mr.btp.api.common.dto.PageResponse;
import mr.btp.api.common.exception.ApiException;
import mr.btp.api.common.service.ReferenceDataService;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
public class InvoiceService {

    private final SupplierInvoiceRepository invoiceRepository;
    private final SupplierInvoiceItemRepository invoiceItemRepository;
    private final ReferenceDataService referenceDataService;

    public InvoiceService(SupplierInvoiceRepository invoiceRepository,
                          SupplierInvoiceItemRepository invoiceItemRepository,
                          ReferenceDataService referenceDataService) {
        this.invoiceRepository = invoiceRepository;
        this.invoiceItemRepository = invoiceItemRepository;
        this.referenceDataService = referenceDataService;
    }

    public PageResponse<InvoiceDtos.InvoiceResponse> list(int page, int size) {
        return PageResponse.from(invoiceRepository.findAll(PageRequest.of(page, size)).map(this::toResponse));
    }

    public InvoiceDtos.InvoiceResponse get(Long id) {
        return toResponse(referenceDataService.getInvoice(id));
    }

    @Transactional
    public InvoiceDtos.InvoiceResponse create(InvoiceDtos.InvoiceRequest request) {
        SupplierInvoice invoice = new SupplierInvoice();
        apply(invoice, request);
        invoice = invoiceRepository.save(invoice);
        replaceItems(invoice, request.items());
        return toResponse(invoice);
    }

    @Transactional
    public InvoiceDtos.InvoiceResponse update(Long id, InvoiceDtos.InvoiceRequest request) {
        SupplierInvoice invoice = referenceDataService.getInvoice(id);
        apply(invoice, request);
        invoice = invoiceRepository.save(invoice);
        invoiceItemRepository.findByInvoiceId(id).forEach(item -> {
            if (referenceDataService.invoiceItemConsumedAmount(item.getId(), null).compareTo(BigDecimal.ZERO) > 0) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Cannot replace items on an invoice that has recorded consumptions");
            }
        });
        invoiceItemRepository.deleteAll(invoiceItemRepository.findByInvoiceId(id));
        replaceItems(invoice, request.items());
        return toResponse(invoice);
    }

    @Transactional
    public InvoiceDtos.InvoiceItemResponse createItem(InvoiceDtos.InvoiceItemUpsertRequest request) {
        SupplierInvoice invoice = referenceDataService.getInvoice(request.invoiceId());
        SupplierInvoiceItem item = new SupplierInvoiceItem();
        item.setInvoice(invoice);
        applyItem(item, request);
        SupplierInvoiceItem saved = invoiceItemRepository.save(item);
        validateInvoiceTotal(invoice);
        return toItemResponse(saved);
    }

    @Transactional
    public InvoiceDtos.InvoiceItemResponse updateItem(Long id, InvoiceDtos.InvoiceItemUpsertRequest request) {
        SupplierInvoiceItem item = referenceDataService.getInvoiceItem(id);
        BigDecimal consumed = referenceDataService.invoiceItemConsumedAmount(item.getId(), null);
        if (consumed.compareTo(request.totalAmount()) > 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Item total cannot be reduced below consumed amount");
        }
        applyItem(item, request);
        SupplierInvoiceItem saved = invoiceItemRepository.save(item);
        validateInvoiceTotal(saved.getInvoice());
        return toItemResponse(saved);
    }

    private void apply(SupplierInvoice invoice, InvoiceDtos.InvoiceRequest request) {
        invoice.setSupplier(referenceDataService.getSupplier(request.supplierId()));
        invoice.setProject(request.projectId() == null ? null : referenceDataService.getProject(request.projectId()));
        invoice.setReference(request.reference());
        invoice.setInvoiceDate(request.invoiceDate());
        invoice.setTotalAmount(request.totalAmount());
        invoice.setCurrency(request.currency().trim().toUpperCase());
        invoice.setNotes(request.notes());
        invoice.setDocumentRef(request.documentRef() == null || request.documentRef().isBlank() ? null : request.documentRef().trim());
        invoice.setStatus(request.status());
    }

    private void replaceItems(SupplierInvoice invoice, List<InvoiceDtos.InvoiceItemUpsertRequest> items) {
        BigDecimal itemSum = items.stream().map(InvoiceDtos.InvoiceItemUpsertRequest::totalAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (itemSum.compareTo(invoice.getTotalAmount()) != 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invoice total must equal the sum of invoice items");
        }
        items.forEach(request -> {
            SupplierInvoiceItem item = new SupplierInvoiceItem();
            item.setInvoice(invoice);
            applyItem(item, request);
            invoiceItemRepository.save(item);
        });
    }

    private void applyItem(SupplierInvoiceItem item, InvoiceDtos.InvoiceItemUpsertRequest request) {
        if (referenceDataService.getCategory(request.categoryId()).getType() != CategoryType.MATERIAL) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Supplier advance items must use a material category");
        }
        item.setCategory(referenceDataService.getCategory(request.categoryId()));
        item.setDescription(request.description().trim());
        item.setQuantity(request.quantity());
        item.setUnit(request.unit());
        item.setUnitPrice(request.unitPrice());
        item.setTotalAmount(request.totalAmount());
    }

    private void validateInvoiceTotal(SupplierInvoice invoice) {
        BigDecimal sum = invoiceItemRepository.findByInvoiceId(invoice.getId()).stream()
                .map(SupplierInvoiceItem::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (sum.compareTo(invoice.getTotalAmount()) != 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invoice total must remain aligned with invoice items");
        }
    }

    private InvoiceDtos.InvoiceResponse toResponse(SupplierInvoice invoice) {
        BigDecimal consumedAmount = referenceDataService.invoiceConsumedAmount(invoice.getId());
        List<InvoiceDtos.InvoiceItemResponse> items = invoiceItemRepository.findByInvoiceId(invoice.getId()).stream()
                .map(this::toItemResponse)
                .toList();
        return new InvoiceDtos.InvoiceResponse(
                invoice.getId(),
                invoice.getSupplier().getId(),
                invoice.getProject() == null ? null : invoice.getProject().getId(),
                invoice.getSupplier().getName(),
                invoice.getReference(),
                invoice.getInvoiceDate(),
                invoice.getTotalAmount(),
                invoice.getCurrency(),
                invoice.getNotes(),
                invoice.getDocumentRef(),
                invoice.getStatus().name(),
                consumedAmount,
                invoice.getTotalAmount().subtract(consumedAmount),
                items
        );
    }

    private InvoiceDtos.InvoiceItemResponse toItemResponse(SupplierInvoiceItem item) {
        BigDecimal consumedAmount = referenceDataService.invoiceItemConsumedAmount(item.getId(), null);
        return new InvoiceDtos.InvoiceItemResponse(
                item.getId(),
                item.getInvoice().getId(),
                item.getCategory().getId(),
                item.getCategory().getName(),
                item.getDescription(),
                item.getQuantity(),
                item.getUnit(),
                item.getUnitPrice(),
                item.getTotalAmount(),
                consumedAmount,
                item.getTotalAmount().subtract(consumedAmount)
        );
    }
}
