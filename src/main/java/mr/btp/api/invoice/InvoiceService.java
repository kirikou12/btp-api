package mr.btp.api.invoice;

import mr.btp.api.category.CategoryType;
import mr.btp.api.common.dto.PageResponse;
import mr.btp.api.common.exception.ApiException;
import mr.btp.api.common.service.ReferenceDataService;
import mr.btp.api.project.ConstructionStage;
import mr.btp.api.project.StageStatus;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class InvoiceService {

    private static final List<InvoiceType> USAGE_INVOICE_TYPES = List.of(InvoiceType.SUPPLY_USAGE, InvoiceType.DIRECT_USAGE);

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

    @Transactional(readOnly = true)
    public PageResponse<InvoiceDtos.InvoiceResponse> list(int page, int size) {
        return list(page, size, null);
    }

    @Transactional(readOnly = true)
    public PageResponse<InvoiceDtos.InvoiceResponse> list(int page, int size, String type) {
        if (type == null || type.isBlank() || "ALL".equalsIgnoreCase(type)) {
            return PageResponse.from(invoiceRepository.findAll(PageRequest.of(page, size)).map(this::toResponse));
        }
        InvoiceType invoiceType = InvoiceType.valueOf(type.trim().toUpperCase());
        List<InvoiceDtos.InvoiceResponse> rows = invoiceRepository.findByInvoiceTypeOrderByInvoiceDateDesc(invoiceType).stream()
                .skip((long) page * size)
                .limit(size)
                .map(this::toResponse)
                .toList();
        long total = invoiceRepository.findByInvoiceTypeOrderByInvoiceDateDesc(invoiceType).size();
        return new PageResponse<>(rows, page, size, total, (int) Math.ceil((double) total / size));
    }

    @Transactional(readOnly = true)
    public InvoiceDtos.InvoiceResponse get(Long id) {
        return toResponse(referenceDataService.getInvoice(id));
    }

    @Transactional(readOnly = true)
    public List<InvoiceDtos.InvoiceResponse> usageInvoicesByProject(Long projectId, Long stageId) {
        referenceDataService.getProject(projectId);
        if (stageId != null) {
            ConstructionStage stage = referenceDataService.getStage(stageId);
            if (!stage.getProject().getId().equals(projectId)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Stage must belong to the selected project");
            }
            return invoiceRepository.findByProjectIdAndStageIdAndInvoiceTypeInOrderByInvoiceDateDescIdDesc(projectId, stageId, USAGE_INVOICE_TYPES).stream()
                    .map(this::toResponse)
                    .toList();
        }
        return invoiceRepository.findByProjectIdAndInvoiceTypeInOrderByInvoiceDateDescIdDesc(projectId, USAGE_INVOICE_TYPES).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public InvoiceDtos.InvoiceResponse create(InvoiceDtos.InvoiceRequest request) {
        if (request.invoiceType() == InvoiceType.SUPPLY_USAGE) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Use /api/usage-invoices to create supply usage invoices");
        }
        SupplierInvoice invoice = new SupplierInvoice();
        apply(invoice, request);
        invoice = invoiceRepository.save(invoice);
        replaceItems(invoice, request.items());
        return toResponse(invoice);
    }

    @Transactional
    public InvoiceDtos.InvoiceResponse update(Long id, InvoiceDtos.InvoiceRequest request) {
        SupplierInvoice invoice = referenceDataService.getInvoice(id);
        if (invoice.getInvoiceType() == InvoiceType.SUPPLY_USAGE || request.invoiceType() == InvoiceType.SUPPLY_USAGE) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Use /api/usage-invoices to update supply usage invoices");
        }
        if (request.invoiceType() != null && invoice.getInvoiceType() != request.invoiceType()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invoice type cannot be changed");
        }
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
    public InvoiceDtos.InvoiceResponse createUsage(InvoiceDtos.UsageInvoiceRequest request) {
        SupplierInvoice source = referenceDataService.getInvoice(request.sourceSupplyInvoiceId());
        validateSupplySource(source);
        ConstructionStage stage = validateUsageStage(request.projectId(), request.stageId(), false);

        SupplierInvoice invoice = new SupplierInvoice();
        invoice.setInvoiceType(InvoiceType.SUPPLY_USAGE);
        invoice.setSupplier(source.getSupplier());
        invoice.setProject(referenceDataService.getProject(request.projectId()));
        invoice.setStage(stage);
        invoice.setSourceSupplyInvoice(source);
        invoice.setInvoiceDate(request.invoiceDate());
        invoice.setCurrency(source.getCurrency());
        invoice.setNotes(request.notes());
        invoice.setDocumentRef(request.documentRef() == null || request.documentRef().isBlank() ? null : request.documentRef().trim());
        invoice.setStatus(request.status());

        List<SupplierInvoiceItem> usageItems = buildUsageItems(invoice, source, request.items(), null);
        invoice.setTotalAmount(usageItems.stream().map(SupplierInvoiceItem::getTotalAmount).reduce(BigDecimal.ZERO, BigDecimal::add));
        SupplierInvoice saved = invoiceRepository.save(invoice);
        usageItems.forEach(item -> {
            item.setInvoice(saved);
            invoiceItemRepository.save(item);
        });
        return toResponse(saved);
    }

    @Transactional
    public InvoiceDtos.InvoiceResponse updateUsage(Long id, InvoiceDtos.UsageInvoiceRequest request) {
        SupplierInvoice invoice = referenceDataService.getInvoice(id);
        if (invoice.getInvoiceType() != InvoiceType.SUPPLY_USAGE) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Only supply usage invoices can be updated here");
        }
        SupplierInvoice source = referenceDataService.getInvoice(request.sourceSupplyInvoiceId());
        validateSupplySource(source);
        ConstructionStage stage = validateUsageStage(request.projectId(), request.stageId(), true);

        List<SupplierInvoiceItem> usageItems = buildUsageItems(invoice, source, request.items(), id);
        invoice.setSupplier(source.getSupplier());
        invoice.setProject(referenceDataService.getProject(request.projectId()));
        invoice.setStage(stage);
        invoice.setSourceSupplyInvoice(source);
        invoice.setInvoiceDate(request.invoiceDate());
        invoice.setCurrency(source.getCurrency());
        invoice.setNotes(request.notes());
        invoice.setDocumentRef(request.documentRef() == null || request.documentRef().isBlank() ? null : request.documentRef().trim());
        invoice.setStatus(request.status());
        invoice.setTotalAmount(usageItems.stream().map(SupplierInvoiceItem::getTotalAmount).reduce(BigDecimal.ZERO, BigDecimal::add));
        invoiceItemRepository.deleteAll(invoiceItemRepository.findByInvoiceId(id));
        SupplierInvoice saved = invoiceRepository.save(invoice);
        usageItems.forEach(item -> {
            item.setInvoice(saved);
            invoiceItemRepository.save(item);
        });
        return toResponse(saved);
    }

    @Transactional
    public void deleteUsage(Long id) {
        SupplierInvoice invoice = referenceDataService.getInvoice(id);
        if (invoice.getInvoiceType() != InvoiceType.SUPPLY_USAGE) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Only supply usage invoices can be deleted here");
        }
        invoiceRepository.delete(invoice);
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
        InvoiceType invoiceType = request.invoiceType() == null ? invoice.getInvoiceType() : request.invoiceType();
        if (invoiceType == InvoiceType.DIRECT_USAGE) {
            if (request.projectId() == null) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Project is required for direct usage invoices");
            }
            if (request.stageId() == null) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Stage is required for direct usage invoices");
            }
            ConstructionStage stage = validateUsageStage(request.projectId(), request.stageId(), false);
            invoice.setStage(stage);
            invoice.setProject(stage.getProject());
        } else {
            invoice.setStage(null);
            invoice.setProject(request.projectId() == null ? null : referenceDataService.getProject(request.projectId()));
        }
        invoice.setInvoiceType(invoiceType);
        invoice.setSourceSupplyInvoice(null);
        invoice.setSupplier(referenceDataService.getSupplier(request.supplierId()));
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
        item.setSourceSupplyItem(null);
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
        BigDecimal consumedAmount = invoice.getInvoiceType() == InvoiceType.SUPPLY ? referenceDataService.invoiceConsumedAmount(invoice.getId()) : invoice.getTotalAmount();
        List<InvoiceDtos.InvoiceItemResponse> items = invoiceItemRepository.findByInvoiceId(invoice.getId()).stream()
                .map(this::toItemResponse)
                .toList();
        return new InvoiceDtos.InvoiceResponse(
                invoice.getId(),
                invoice.getInvoiceType(),
                invoice.getSupplier().getId(),
                invoice.getProject() == null ? null : invoice.getProject().getId(),
                invoice.getStage() == null ? null : invoice.getStage().getId(),
                invoice.getStage() == null ? null : invoice.getStage().getName(),
                invoice.getSourceSupplyInvoice() == null ? null : invoice.getSourceSupplyInvoice().getId(),
                invoice.getSourceSupplyInvoice() == null ? null : invoice.getSourceSupplyInvoice().getReference(),
                invoice.getSupplier().getName(),
                invoice.getReference(),
                invoice.getInvoiceDate(),
                invoice.getTotalAmount(),
                invoice.getCurrency(),
                invoice.getNotes(),
                invoice.getDocumentRef(),
                invoice.getStatus().name(),
                consumedAmount,
                invoice.getInvoiceType() == InvoiceType.SUPPLY ? invoice.getTotalAmount().subtract(consumedAmount) : BigDecimal.ZERO,
                items
        );
    }

    private InvoiceDtos.InvoiceItemResponse toItemResponse(SupplierInvoiceItem item) {
        BigDecimal consumedAmount = item.getInvoice().getInvoiceType() == InvoiceType.SUPPLY
                ? referenceDataService.invoiceItemConsumedAmount(item.getId(), null)
                : BigDecimal.ZERO;
        BigDecimal remainingAmount = item.getInvoice().getInvoiceType() == InvoiceType.SUPPLY
                ? item.getTotalAmount().subtract(consumedAmount)
                : BigDecimal.ZERO;
        return new InvoiceDtos.InvoiceItemResponse(
                item.getId(),
                item.getInvoice().getId(),
                item.getSourceSupplyItem() == null ? null : item.getSourceSupplyItem().getId(),
                item.getCategory().getId(),
                item.getCategory().getName(),
                item.getDescription(),
                item.getQuantity(),
                item.getUnit(),
                item.getUnitPrice(),
                item.getTotalAmount(),
                consumedAmount,
                remainingAmount,
                item.getInvoice().getInvoiceType() == InvoiceType.SUPPLY ? availableQuantity(item, null) : null
        );
    }

    private void validateSupplySource(SupplierInvoice source) {
        if (source.getInvoiceType() != InvoiceType.SUPPLY) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Source invoice must be a SUPPLY invoice");
        }
    }

    private ConstructionStage validateUsageStage(Long projectId, Long stageId, boolean allowCompletedStage) {
        ConstructionStage stage = referenceDataService.getStage(stageId);
        if (!stage.getProject().getId().equals(projectId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Stage must belong to the selected project");
        }
        if (stage.getStatus() == StageStatus.COMPLETED && !allowCompletedStage) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Cannot record usage on a completed stage");
        }
        return stage;
    }

    private List<SupplierInvoiceItem> buildUsageItems(SupplierInvoice usageInvoice,
                                                      SupplierInvoice source,
                                                      List<InvoiceDtos.UsageInvoiceItemRequest> requests,
                                                      Long excludingUsageInvoiceId) {
        List<Long> sourceItemIds = requests.stream().map(InvoiceDtos.UsageInvoiceItemRequest::sourceSupplyItemId).distinct().toList();
        Map<Long, SupplierInvoiceItem> sourceItems = invoiceItemRepository.findByIdIn(sourceItemIds).stream()
                .collect(Collectors.toMap(SupplierInvoiceItem::getId, Function.identity()));
        List<SupplierInvoiceItem> usageItems = requests.stream()
                .filter(request -> request.quantityUsed().compareTo(BigDecimal.ZERO) > 0)
                .map(request -> {
                    SupplierInvoiceItem sourceItem = sourceItems.get(request.sourceSupplyItemId());
                    if (sourceItem == null || !sourceItem.getInvoice().getId().equals(source.getId())) {
                        throw new ApiException(HttpStatus.BAD_REQUEST, "Usage line must reference an item from the source SUPPLY invoice");
                    }
                    if (sourceItem.getQuantity() == null || sourceItem.getUnitPrice() == null) {
                        throw new ApiException(HttpStatus.BAD_REQUEST, "Source SUPPLY item must have quantity and unit price");
                    }
                    BigDecimal available = availableQuantity(sourceItem, excludingUsageInvoiceId);
                    if (request.quantityUsed().compareTo(available) > 0) {
                        throw new ApiException(HttpStatus.BAD_REQUEST, "Usage quantity exceeds available supply quantity");
                    }
                    SupplierInvoiceItem item = new SupplierInvoiceItem();
                    item.setInvoice(usageInvoice);
                    item.setSourceSupplyItem(sourceItem);
                    item.setCategory(sourceItem.getCategory());
                    item.setDescription(sourceItem.getDescription());
                    item.setQuantity(request.quantityUsed());
                    item.setUnit(sourceItem.getUnit());
                    item.setUnitPrice(sourceItem.getUnitPrice());
                    item.setTotalAmount(request.quantityUsed().multiply(sourceItem.getUnitPrice()));
                    return item;
                })
                .toList();
        if (usageItems.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Usage invoice must contain at least one positive line");
        }
        return usageItems;
    }

    private BigDecimal availableQuantity(SupplierInvoiceItem sourceItem, Long excludingUsageInvoiceId) {
        BigDecimal initialQuantity = sourceItem.getQuantity() == null ? BigDecimal.ZERO : sourceItem.getQuantity();
        BigDecimal usedQuantity = invoiceItemRepository.findBySourceSupplyItemId(sourceItem.getId()).stream()
                .filter(item -> excludingUsageInvoiceId == null || !item.getInvoice().getId().equals(excludingUsageInvoiceId))
                .map(SupplierInvoiceItem::getQuantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return initialQuantity.subtract(usedQuantity);
    }
}
