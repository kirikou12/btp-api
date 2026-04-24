package mr.btp.api.category;

import java.util.List;
import mr.btp.api.invoice.SupplierInvoiceItemRepository;
import mr.btp.api.common.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class CategoryService {

    private final ExpenseCategoryRepository categoryRepository;
    private final SupplierInvoiceItemRepository supplierInvoiceItemRepository;

    public CategoryService(ExpenseCategoryRepository categoryRepository,
                           SupplierInvoiceItemRepository supplierInvoiceItemRepository) {
        this.categoryRepository = categoryRepository;
        this.supplierInvoiceItemRepository = supplierInvoiceItemRepository;
    }

    public List<CategoryDtos.CategoryResponse> list() {
        return categoryRepository.findAll().stream().map(this::toResponse).toList();
    }

    public CategoryDtos.CategoryResponse create(CategoryDtos.CategoryRequest request) {
        categoryRepository.findByNameIgnoreCase(request.name()).ifPresent(existing -> {
            throw new ApiException(HttpStatus.CONFLICT, "error.category.already-exists", "Category already exists");
        });
        ExpenseCategory category = new ExpenseCategory();
        apply(category, request);
        return toResponse(categoryRepository.save(category));
    }

    public CategoryDtos.CategoryResponse update(Long id, CategoryDtos.CategoryRequest request) {
        ExpenseCategory category = categoryRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "error.resource.not-found", "{0} not found", mr.btp.api.common.i18n.MessageKey.of("resource.category", "Category")));
        apply(category, request);
        return toResponse(categoryRepository.save(category));
    }

    public void delete(Long id) {
        ExpenseCategory category = categoryRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "error.resource.not-found", "{0} not found", mr.btp.api.common.i18n.MessageKey.of("resource.category", "Category")));

        if (category.isSystem()) {
            throw new ApiException(HttpStatus.CONFLICT, "error.category.system-delete-forbidden", "System categories cannot be deleted");
        }

        long supplierInvoiceItemCount = supplierInvoiceItemRepository.countByCategoryId(id);

        if (supplierInvoiceItemCount > 0) {
            throw new ApiException(HttpStatus.CONFLICT, "error.category.in-use", "Category is in use");
        }

        categoryRepository.delete(category);
    }

    private void apply(ExpenseCategory category, CategoryDtos.CategoryRequest request) {
        category.setName(request.name().trim());
        category.setType(request.type());
        category.setSystem(Boolean.TRUE.equals(request.isSystem()));
    }

    private CategoryDtos.CategoryResponse toResponse(ExpenseCategory category) {
        return new CategoryDtos.CategoryResponse(category.getId(), category.getName(), category.getType().name(), category.isSystem());
    }
}
