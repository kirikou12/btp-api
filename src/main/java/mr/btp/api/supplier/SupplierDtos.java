package mr.btp.api.supplier;

import jakarta.validation.constraints.NotBlank;

public final class SupplierDtos {

    private SupplierDtos() {
    }

    public record SupplierRequest(@NotBlank(message = "{validation.supplier.name.required}") String name, String phone, String email, String address, String notes) {
    }

    public record SupplierResponse(Long id, String name, String phone, String email, String address, String notes) {
    }
}
