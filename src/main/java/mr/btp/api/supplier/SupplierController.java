package mr.btp.api.supplier;

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
@RequestMapping("/api/suppliers")
public class SupplierController {

    private final SupplierService supplierService;

    public SupplierController(SupplierService supplierService) {
        this.supplierService = supplierService;
    }

    @GetMapping
    public PageResponse<SupplierDtos.SupplierResponse> list(@RequestParam(defaultValue = "0") int page,
                                                            @RequestParam(defaultValue = "20") int size) {
        return supplierService.list(page, size);
    }

    @PostMapping
    public SupplierDtos.SupplierResponse create(@Valid @RequestBody SupplierDtos.SupplierRequest request) {
        return supplierService.create(request);
    }

    @GetMapping("/{id}")
    public SupplierDtos.SupplierResponse get(@PathVariable Long id) {
        return supplierService.get(id);
    }

    @PutMapping("/{id}")
    public SupplierDtos.SupplierResponse update(@PathVariable Long id, @Valid @RequestBody SupplierDtos.SupplierRequest request) {
        return supplierService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        supplierService.delete(id);
    }
}
