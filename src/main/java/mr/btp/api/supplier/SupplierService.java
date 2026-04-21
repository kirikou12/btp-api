package mr.btp.api.supplier;

import mr.btp.api.common.dto.PageResponse;
import mr.btp.api.common.exception.ApiException;
import mr.btp.api.common.service.ReferenceDataService;
import mr.btp.api.invoice.SupplierInvoiceRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SupplierService {

    private final SupplierRepository supplierRepository;
    private final ReferenceDataService referenceDataService;
    private final SupplierInvoiceRepository invoiceRepository;

    public SupplierService(SupplierRepository supplierRepository,
                           ReferenceDataService referenceDataService,
                           SupplierInvoiceRepository invoiceRepository) {
        this.supplierRepository = supplierRepository;
        this.referenceDataService = referenceDataService;
        this.invoiceRepository = invoiceRepository;
    }

    public PageResponse<SupplierDtos.SupplierResponse> list(int page, int size) {
        return PageResponse.from(supplierRepository.findAll(PageRequest.of(page, size)).map(this::toResponse));
    }

    public SupplierDtos.SupplierResponse get(Long id) {
        return toResponse(referenceDataService.getSupplier(id));
    }

    @Transactional
    public SupplierDtos.SupplierResponse create(SupplierDtos.SupplierRequest request) {
        Supplier supplier = new Supplier();
        apply(supplier, request);
        return toResponse(supplierRepository.save(supplier));
    }

    @Transactional
    public SupplierDtos.SupplierResponse update(Long id, SupplierDtos.SupplierRequest request) {
        Supplier supplier = referenceDataService.getSupplier(id);
        apply(supplier, request);
        return toResponse(supplierRepository.save(supplier));
    }

    @Transactional
    public void delete(Long id) {
        Supplier supplier = referenceDataService.getSupplier(id);
        if (invoiceRepository.countBySupplier_Id(id) > 0) {
            throw new ApiException(HttpStatus.CONFLICT, "Supplier is in use");
        }
        supplierRepository.delete(supplier);
    }

    private void apply(Supplier supplier, SupplierDtos.SupplierRequest request) {
        supplier.setName(request.name().trim());
        supplier.setPhone(request.phone());
        supplier.setEmail(request.email());
        supplier.setAddress(request.address());
        supplier.setNotes(request.notes());
    }

    private SupplierDtos.SupplierResponse toResponse(Supplier supplier) {
        return new SupplierDtos.SupplierResponse(
                supplier.getId(),
                supplier.getName(),
                supplier.getPhone(),
                supplier.getEmail(),
                supplier.getAddress(),
                supplier.getNotes()
        );
    }
}
