package mr.btp.api.supplier;

import mr.btp.api.common.dto.PageResponse;
import mr.btp.api.common.service.ReferenceDataService;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SupplierService {

    private final SupplierRepository supplierRepository;
    private final ReferenceDataService referenceDataService;

    public SupplierService(SupplierRepository supplierRepository, ReferenceDataService referenceDataService) {
        this.supplierRepository = supplierRepository;
        this.referenceDataService = referenceDataService;
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
