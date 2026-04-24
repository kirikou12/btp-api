package mr.btp.api.invoice;

import mr.btp.api.category.CategoryType;
import mr.btp.api.common.dto.PageResponse;
import mr.btp.api.common.exception.ApiException;
import mr.btp.api.common.i18n.MessageKey;
import mr.btp.api.common.service.ReferenceDataService;
import mr.btp.api.project.ConstructionStage;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class InvoiceService {

    private static final List<InvoiceType> USAGE_INVOICE_TYPES = List.of(InvoiceType.SUPPLY_USAGE, InvoiceType.DIRECT_USAGE, InvoiceType.DIRECT_EXPENSE);

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
        return list(page, size, null, null);
    }

    @Transactional(readOnly = true)
    public PageResponse<InvoiceDtos.InvoiceResponse> list(int page, int size, String type) {
        return list(page, size, type, null);
    }

    @Transactional(readOnly = true)
    public PageResponse<InvoiceDtos.InvoiceResponse> list(int page, int size, String type, Long projectId) {
        if (projectId != null) {
            referenceDataService.getProject(projectId);
        }

        InvoiceType invoiceType = parseInvoiceTypeFilter(type);
        Page<SupplierInvoice> invoices = invoiceRepository.findForProjectAndType(projectId, invoiceType, PageRequest.of(page, size));
        return new PageResponse<>(
                toResponses(invoices.getContent()),
                invoices.getNumber(),
                invoices.getSize(),
                invoices.getTotalElements(),
                invoices.getTotalPages()
        );
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
                throw new ApiException(HttpStatus.BAD_REQUEST, "error.stage.project-mismatch", "Stage must belong to the selected project");
            }
            return toResponses(invoiceRepository.findByProjectIdAndStageIdAndInvoiceTypeInOrderByInvoiceDateDescIdDesc(projectId, stageId, USAGE_INVOICE_TYPES));
        }
        return toResponses(invoiceRepository.findByProjectIdAndInvoiceTypeInOrderByInvoiceDateDescIdDesc(projectId, USAGE_INVOICE_TYPES));
    }

    @Transactional
    public InvoiceDtos.InvoiceResponse create(InvoiceDtos.InvoiceRequest request) {
        if (request.invoiceType() == InvoiceType.SUPPLY_USAGE) {
            return createUsageFromInvoiceRequest(request);
        }
        if (request.invoiceType() == InvoiceType.SUPPLY_RETURN) {
            return createReturnFromInvoiceRequest(request);
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
        if (request.invoiceType() != null && invoice.getInvoiceType() != request.invoiceType()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "error.invoice.type-immutable", "Invoice type cannot be changed");
        }
        if (invoice.getInvoiceType() == InvoiceType.SUPPLY_USAGE) {
            return updateUsageFromInvoiceRequest(invoice, request);
        }
        if (invoice.getInvoiceType() == InvoiceType.SUPPLY_RETURN) {
            return updateReturnFromInvoiceRequest(invoice, request);
        }
        if (request.invoiceType() == InvoiceType.SUPPLY_USAGE) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "error.invoice.type-immutable", "Invoice type cannot be changed");
        }
        if (request.invoiceType() == InvoiceType.SUPPLY_RETURN) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "error.invoice.type-immutable", "Invoice type cannot be changed");
        }
        apply(invoice, request);
        invoice = invoiceRepository.save(invoice);
        if (invoice.getInvoiceType() == InvoiceType.SUPPLY) {
            syncSupplyItems(invoice, request.items());
        } else {
            invoiceItemRepository.deleteAll(invoiceItemRepository.findByInvoiceId(id));
            replaceItems(invoice, request.items());
        }
        return toResponse(invoice);
    }

    @Transactional
    public InvoiceDtos.InvoiceResponse createUsage(InvoiceDtos.UsageInvoiceRequest request) {
        return create(toInvoiceRequest(request));
    }

    @Transactional
    public InvoiceDtos.InvoiceResponse updateUsage(Long id, InvoiceDtos.UsageInvoiceRequest request) {
        return update(id, toInvoiceRequest(request));
    }

    private InvoiceDtos.InvoiceRequest toInvoiceRequest(InvoiceDtos.UsageInvoiceRequest request) {
        return new InvoiceDtos.InvoiceRequest(
                InvoiceType.SUPPLY_USAGE,
                null,
                request.projectId(),
                request.stageId(),
                request.sourceSupplyInvoiceId(),
                null,
                request.invoiceDate(),
                null,
                null,
                request.notes(),
                request.documentRef(),
                request.status(),
                request.items().stream()
                        .map(item -> new InvoiceDtos.InvoiceItemUpsertRequest(
                                null,
                                item.sourceSupplyItemId(),
                                null,
                                null,
                                item.quantityUsed(),
                                null,
                                null,
                                null,
                                null
                        ))
                        .toList()
        );
    }

    private InvoiceDtos.InvoiceResponse createUsageFromInvoiceRequest(InvoiceDtos.InvoiceRequest request) {
        SupplierInvoice source = getUsageSource(request);
        ConstructionStage stage = getUsageStage(request);

        SupplierInvoice invoice = new SupplierInvoice();
        applyUsageInvoice(invoice, request, source, stage);

        List<SupplierInvoiceItem> usageItems = buildUsageItems(invoice, source, request.items(), null);
        invoice.setTotalAmount(sumItemTotals(usageItems));
        if (invoice.getNotes() == null || invoice.getNotes().isBlank()) {
            invoice.setNotes(generateNotesFromItems(usageItems));
        }
        SupplierInvoice saved = invoiceRepository.save(invoice);
        usageItems.forEach(item -> {
            item.setInvoice(saved);
            invoiceItemRepository.save(item);
        });
        return toResponse(saved);
    }

    private InvoiceDtos.InvoiceResponse updateUsageFromInvoiceRequest(SupplierInvoice invoice, InvoiceDtos.InvoiceRequest request) {
        SupplierInvoice source = getUsageSource(request);
        ConstructionStage stage = getUsageStage(request);

        List<SupplierInvoiceItem> usageItems = buildUsageItems(invoice, source, request.items(), invoice.getId());
        applyUsageInvoice(invoice, request, source, stage);
        invoice.setTotalAmount(sumItemTotals(usageItems));
        if (invoice.getNotes() == null || invoice.getNotes().isBlank()) {
            invoice.setNotes(generateNotesFromItems(usageItems));
        }
        invoiceItemRepository.deleteAll(invoiceItemRepository.findByInvoiceId(invoice.getId()));
        SupplierInvoice saved = invoiceRepository.save(invoice);
        usageItems.forEach(item -> {
            item.setInvoice(saved);
            invoiceItemRepository.save(item);
        });
        return toResponse(saved);
    }

    private InvoiceDtos.InvoiceResponse createReturnFromInvoiceRequest(InvoiceDtos.InvoiceRequest request) {
        SupplierInvoice source = getReturnSource(request);

        SupplierInvoice invoice = new SupplierInvoice();
        applyReturnInvoice(invoice, request, source);

        List<SupplierInvoiceItem> returnItems = buildReturnItems(invoice, source, request.items(), null);
        invoice.setTotalAmount(sumItemTotals(returnItems));
        if (invoice.getNotes() == null || invoice.getNotes().isBlank()) {
            invoice.setNotes(generateNotesFromItems(returnItems));
        }
        SupplierInvoice saved = invoiceRepository.save(invoice);
        returnItems.forEach(item -> {
            item.setInvoice(saved);
            invoiceItemRepository.save(item);
        });
        return toResponse(saved);
    }

    private InvoiceDtos.InvoiceResponse updateReturnFromInvoiceRequest(SupplierInvoice invoice, InvoiceDtos.InvoiceRequest request) {
        SupplierInvoice source = getReturnSource(request);

        List<SupplierInvoiceItem> returnItems = buildReturnItems(invoice, source, request.items(), invoice.getId());
        applyReturnInvoice(invoice, request, source);
        invoice.setTotalAmount(sumItemTotals(returnItems));
        if (invoice.getNotes() == null || invoice.getNotes().isBlank()) {
            invoice.setNotes(generateNotesFromItems(returnItems));
        }
        invoiceItemRepository.deleteAll(invoiceItemRepository.findByInvoiceId(invoice.getId()));
        SupplierInvoice saved = invoiceRepository.save(invoice);
        returnItems.forEach(item -> {
            item.setInvoice(saved);
            invoiceItemRepository.save(item);
        });
        return toResponse(saved);
    }

    private SupplierInvoice getUsageSource(InvoiceDtos.InvoiceRequest request) {
        if (request.sourceSupplyInvoiceId() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "error.invoice.source-supply.required-for-usage", "Source supply invoice is required for usage invoices");
        }
        SupplierInvoice source = referenceDataService.getInvoice(request.sourceSupplyInvoiceId());
        validateSupplySource(source);
        return source;
    }

    private SupplierInvoice getReturnSource(InvoiceDtos.InvoiceRequest request) {
        if (request.sourceSupplyInvoiceId() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "error.invoice.source-supply.required-for-return", "Source supply invoice is required for return invoices");
        }
        SupplierInvoice source = referenceDataService.getInvoice(request.sourceSupplyInvoiceId());
        validateSupplySource(source);
        return source;
    }

    private ConstructionStage getUsageStage(InvoiceDtos.InvoiceRequest request) {
        if (request.projectId() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "error.invoice.project.required-for-usage", "Project is required for usage invoices");
        }
        if (request.stageId() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "error.invoice.stage.required-for-usage", "Stage is required for usage invoices");
        }
        return validateUsageStage(request.projectId(), request.stageId());
    }

    private void applyUsageInvoice(SupplierInvoice invoice, InvoiceDtos.InvoiceRequest request, SupplierInvoice source, ConstructionStage stage) {
        invoice.setInvoiceType(InvoiceType.SUPPLY_USAGE);
        invoice.setSupplier(source.getSupplier());
        invoice.setProject(stage.getProject());
        invoice.setStage(stage);
        invoice.setSourceSupplyInvoice(source);
        invoice.setReference(request.reference());
        invoice.setInvoiceDate(request.invoiceDate());
        invoice.setCurrency(source.getCurrency());
        invoice.setNotes(request.notes());
        invoice.setDocumentRef(request.documentRef() == null || request.documentRef().isBlank() ? null : request.documentRef().trim());
        invoice.setStatus(request.status());
    }

    private void applyReturnInvoice(SupplierInvoice invoice, InvoiceDtos.InvoiceRequest request, SupplierInvoice source) {
        invoice.setInvoiceType(InvoiceType.SUPPLY_RETURN);
        invoice.setSupplier(source.getSupplier());
        invoice.setProject(source.getProject());
        invoice.setStage(null);
        invoice.setSourceSupplyInvoice(source);
        invoice.setReference(request.reference());
        invoice.setInvoiceDate(request.invoiceDate());
        invoice.setCurrency(source.getCurrency());
        invoice.setNotes(request.notes());
        invoice.setDocumentRef(request.documentRef() == null || request.documentRef().isBlank() ? null : request.documentRef().trim());
        invoice.setStatus(request.status());
    }

    private InvoiceType parseInvoiceTypeFilter(String type) {
        if (type == null || type.isBlank() || "ALL".equalsIgnoreCase(type)) {
            return null;
        }

        try {
            return InvoiceType.valueOf(type.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "error.invoice.type.unsupported", "Unsupported invoice type");
        }
    }

    @Transactional
    public void delete(Long id) {
        SupplierInvoice invoice = referenceDataService.getInvoice(id);
        if (invoice.getInvoiceType() == InvoiceType.SUPPLY) {
            invoiceItemRepository.findByInvoiceId(id).forEach(item -> {
                if (referenceDataService.invoiceItemOutgoingAmount(item.getId(), null).compareTo(BigDecimal.ZERO) > 0) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "error.invoice.delete.supply-has-outgoing", "Cannot delete a supply invoice that has recorded outgoing quantities");
                }
            });
        }
        invoiceRepository.delete(invoice);
    }

    @Transactional
    public void deleteUsage(Long id) {
        SupplierInvoice invoice = referenceDataService.getInvoice(id);
        if (invoice.getInvoiceType() != InvoiceType.SUPPLY_USAGE) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "error.invoice.delete.only-supply-usage-here", "Only supply usage invoices can be deleted here");
        }
        invoiceRepository.delete(invoice);
    }

    @Transactional
    public void deleteReturn(Long id) {
        SupplierInvoice invoice = referenceDataService.getInvoice(id);
        if (invoice.getInvoiceType() != InvoiceType.SUPPLY_RETURN) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "error.invoice.delete.only-supply-return-here", "Only supply return invoices can be deleted here");
        }
        invoiceRepository.delete(invoice);
    }

    @Transactional
    public InvoiceDtos.InvoiceItemResponse createItem(InvoiceDtos.InvoiceItemUpsertRequest request) {
        SupplierInvoice invoice = referenceDataService.getInvoice(request.invoiceId());
        SupplierInvoiceItem item = new SupplierInvoiceItem();
        item.setInvoice(invoice);
        applyItem(item, request, invoice.getInvoiceType());
        SupplierInvoiceItem saved = invoiceItemRepository.save(item);
        validateInvoiceTotal(invoice);
        return toItemResponse(saved);
    }

    @Transactional
    public InvoiceDtos.InvoiceItemResponse updateItem(Long id, InvoiceDtos.InvoiceItemUpsertRequest request) {
        SupplierInvoiceItem item = referenceDataService.getInvoiceItem(id);
        BigDecimal outgoing = referenceDataService.invoiceItemOutgoingAmount(item.getId(), null);
        if (outgoing.compareTo(request.totalAmount()) > 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "error.invoice.item.total-update-below-outgoing", "Item total cannot be reduced below outgoing amount");
        }
        applyItem(item, request, item.getInvoice().getInvoiceType());
        SupplierInvoiceItem saved = invoiceItemRepository.save(item);
        validateInvoiceTotal(saved.getInvoice());
        return toItemResponse(saved);
    }

    private void apply(SupplierInvoice invoice, InvoiceDtos.InvoiceRequest request) {
        InvoiceType invoiceType = request.invoiceType() == null ? invoice.getInvoiceType() : request.invoiceType();
        if (invoiceType == null) {
            invoiceType = InvoiceType.SUPPLY;
        }
        if (invoiceType == InvoiceType.SUPPLY_USAGE || invoiceType == InvoiceType.SUPPLY_RETURN) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "error.invoice.type-dedicated-path",
                    "{0} invoices must be handled by the dedicated invoice path",
                    invoiceTypeLabel(invoiceType)
            );
        }
        if (invoiceType != InvoiceType.DIRECT_EXPENSE && request.supplierId() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "error.invoice.supplier.required", "Supplier is required");
        }
        if (request.totalAmount() == null || request.totalAmount().compareTo(BigDecimal.ZERO) < 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "error.invoice.total.required", "Invoice total is required");
        }
        if (request.currency() == null || request.currency().isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "error.invoice.currency.required", "Currency is required");
        }
        if (invoiceType == InvoiceType.DIRECT_USAGE || invoiceType == InvoiceType.DIRECT_EXPENSE) {
            if (request.projectId() == null) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "error.invoice.project.required-for-direct", "Project is required for direct usage and direct expense invoices");
            }
            if (request.stageId() == null) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "error.invoice.stage.required-for-direct", "Stage is required for direct usage and direct expense invoices");
            }
            ConstructionStage stage = validateUsageStage(request.projectId(), request.stageId());
            invoice.setStage(stage);
            invoice.setProject(stage.getProject());
        } else {
            invoice.setStage(null);
            invoice.setProject(request.projectId() == null ? null : referenceDataService.getProject(request.projectId()));
        }
        invoice.setInvoiceType(invoiceType);
        invoice.setSourceSupplyInvoice(null);
        invoice.setSupplier(request.supplierId() != null ? referenceDataService.getSupplier(request.supplierId()) : null);
        invoice.setReference(request.reference());
        invoice.setInvoiceDate(request.invoiceDate());
        invoice.setTotalAmount(request.totalAmount());
        invoice.setCurrency(request.currency().trim().toUpperCase());
        invoice.setNotes(request.notes() == null || request.notes().isBlank() ? generateNotesFromRequests(request.items()) : request.notes().trim());
        invoice.setDocumentRef(request.documentRef() == null || request.documentRef().isBlank() ? null : request.documentRef().trim());
        invoice.setStatus(request.status());
    }

    private void replaceItems(SupplierInvoice invoice, List<InvoiceDtos.InvoiceItemUpsertRequest> items) {
        if (items == null || items.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "error.invoice.items.required", "Invoice must contain at least one item");
        }
        BigDecimal itemSum = BigDecimal.ZERO;
        for (InvoiceDtos.InvoiceItemUpsertRequest item : items) {
            validateRegularItemRequest(item);
            itemSum = itemSum.add(item.totalAmount());
        }
        if (itemSum.compareTo(invoice.getTotalAmount()) != 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "error.invoice.total.sum-mismatch", "Invoice total must equal the sum of invoice items");
        }
        items.forEach(request -> {
            SupplierInvoiceItem item = new SupplierInvoiceItem();
            item.setInvoice(invoice);
            applyItem(item, request, invoice.getInvoiceType());
            invoiceItemRepository.save(item);
        });
    }

    private void syncSupplyItems(SupplierInvoice invoice, List<InvoiceDtos.InvoiceItemUpsertRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "error.invoice.items.required", "Invoice must contain at least one item");
        }

        BigDecimal itemSum = BigDecimal.ZERO;
        for (InvoiceDtos.InvoiceItemUpsertRequest request : requests) {
            validateRegularItemRequest(request);
            itemSum = itemSum.add(request.totalAmount());
        }
        if (itemSum.compareTo(invoice.getTotalAmount()) != 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "error.invoice.total.sum-mismatch", "Invoice total must equal the sum of invoice items");
        }

        List<SupplierInvoiceItem> existingItems = invoiceItemRepository.findByInvoiceId(invoice.getId());
        List<ResolvedSupplyItemUpdate> resolvedRequests = resolveSupplyItemUpdates(existingItems, requests);
        Set<Long> retainedItemIds = new HashSet<>();
        Set<Long> outgoingInvoiceIdsToRecalculate = new HashSet<>();

        for (ResolvedSupplyItemUpdate resolvedRequest : resolvedRequests) {
            SupplierInvoiceItem item = resolvedRequest.existingItem();
            if (item == null) {
                item = new SupplierInvoiceItem();
                item.setInvoice(invoice);
            } else {
                retainedItemIds.add(item.getId());
                validateSupplyItemEdit(item, resolvedRequest.request());
            }
            applyItem(item, resolvedRequest.request(), invoice.getInvoiceType());
            SupplierInvoiceItem saved = invoiceItemRepository.save(item);
            syncDependentOutgoingItems(saved, outgoingInvoiceIdsToRecalculate);
        }

        for (SupplierInvoiceItem existingItem : existingItems) {
            if (retainedItemIds.contains(existingItem.getId())) {
                continue;
            }
            validateSupplyItemDeletion(existingItem);
            invoiceItemRepository.delete(existingItem);
        }

        recalculateInvoiceTotals(outgoingInvoiceIdsToRecalculate);
    }

    private List<ResolvedSupplyItemUpdate> resolveSupplyItemUpdates(List<SupplierInvoiceItem> existingItems,
                                                                    List<InvoiceDtos.InvoiceItemUpsertRequest> requests) {
        Map<Long, SupplierInvoiceItem> existingById = existingItems.stream()
                .collect(Collectors.toMap(SupplierInvoiceItem::getId, Function.identity()));
        boolean usesExplicitIds = requests.stream().anyMatch(request -> request.id() != null);
        Set<Long> usedIds = new HashSet<>();
        List<ResolvedSupplyItemUpdate> resolved = new ArrayList<>();

        for (int index = 0; index < requests.size(); index++) {
            InvoiceDtos.InvoiceItemUpsertRequest request = requests.get(index);
            SupplierInvoiceItem existingItem = null;
            if (request.id() != null) {
                existingItem = existingById.get(request.id());
                if (existingItem == null) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "error.invoice.item.update.not-in-invoice", "Invoice item does not belong to the invoice being updated");
                }
                if (!usedIds.add(existingItem.getId())) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "error.invoice.item.update.duplicate", "Invoice item cannot be updated more than once");
                }
            } else if (!usesExplicitIds && index < existingItems.size()) {
                existingItem = existingItems.get(index);
                usedIds.add(existingItem.getId());
            }
            resolved.add(new ResolvedSupplyItemUpdate(existingItem, request));
        }

        return resolved;
    }

    private void validateSupplyItemEdit(SupplierInvoiceItem existingItem, InvoiceDtos.InvoiceItemUpsertRequest request) {
        SourceItemUsageAmounts outgoing = outgoingAmounts(existingItem.getId(), null);
        BigDecimal requestedQuantity = request.quantity() == null ? BigDecimal.ZERO : request.quantity();
        if (requestedQuantity.compareTo(outgoing.quantity()) < 0) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "error.invoice.item-quantity-below-outgoing",
                    "Item ''{0}'' quantity cannot be reduced below outgoing quantity. Outgoing quantity: {1}",
                    existingItem.getDescription(),
                    outgoing.quantity().toPlainString()
            );
        }
        if (request.totalAmount().compareTo(outgoing.amount()) < 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "error.invoice.item.total-below-outgoing", "Item total amount cannot be reduced below outgoing amount");
        }
        if (outgoing.quantity().compareTo(BigDecimal.ZERO) > 0 && request.unitPrice() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "error.invoice.source-item.unit-price-required", "Unit price is required when outgoing quantities exist");
        }
    }

    private MessageKey invoiceTypeLabel(InvoiceType invoiceType) {
        return switch (invoiceType) {
            case SUPPLY -> MessageKey.of("invoice-type.supply", "supply");
            case SUPPLY_USAGE -> MessageKey.of("invoice-type.supply-usage", "supply usage");
            case SUPPLY_RETURN -> MessageKey.of("invoice-type.supply-return", "supply return");
            case DIRECT_USAGE -> MessageKey.of("invoice-type.direct-usage", "direct usage");
            case DIRECT_EXPENSE -> MessageKey.of("invoice-type.direct-expense", "direct expense");
        };
    }

    private void validateSupplyItemDeletion(SupplierInvoiceItem item) {
        if (outgoingAmounts(item.getId(), null).quantity().compareTo(BigDecimal.ZERO) > 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "error.invoice-item.delete.supply-has-outgoing", "Cannot delete a supply item that has recorded outgoing quantities");
        }
    }

    private void syncDependentOutgoingItems(SupplierInvoiceItem sourceItem, Set<Long> outgoingInvoiceIdsToRecalculate) {
        if (sourceItem.getId() == null) {
            return;
        }
        List<SupplierInvoiceItem> outgoingItems = invoiceItemRepository.findBySourceSupplyItemId(sourceItem.getId());
        if (outgoingItems.isEmpty()) {
            return;
        }
        if (sourceItem.getUnitPrice() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "error.invoice.source-item.unit-price-required", "Source SUPPLY item must have unit price when outgoing quantities exist");
        }

        for (SupplierInvoiceItem outgoingItem : outgoingItems) {
            outgoingItem.setCategory(sourceItem.getCategory());
            outgoingItem.setDescription(sourceItem.getDescription());
            outgoingItem.setUnit(sourceItem.getUnit());
            outgoingItem.setUnitPrice(sourceItem.getUnitPrice());
            outgoingItem.setTotalAmount(defaultZero(outgoingItem.getQuantity()).multiply(sourceItem.getUnitPrice()));
            invoiceItemRepository.save(outgoingItem);
            outgoingInvoiceIdsToRecalculate.add(outgoingItem.getInvoice().getId());
        }
    }

    private void recalculateInvoiceTotals(Set<Long> invoiceIds) {
        for (Long invoiceId : invoiceIds) {
            SupplierInvoice invoice = referenceDataService.getInvoice(invoiceId);
            invoice.setTotalAmount(sumItemTotals(invoiceItemRepository.findByInvoiceId(invoiceId)));
            invoiceRepository.save(invoice);
        }
    }

    private void applyItem(SupplierInvoiceItem item, InvoiceDtos.InvoiceItemUpsertRequest request, InvoiceType invoiceType) {
        validateRegularItemRequest(request);
        CategoryType categoryType = referenceDataService.getCategory(request.categoryId()).getType();
        if (invoiceType != InvoiceType.DIRECT_EXPENSE && categoryType != CategoryType.MATERIAL) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "error.invoice.supplier-advance.material-category-required", "Supplier advance items must use a material category");
        }
        item.setCategory(referenceDataService.getCategory(request.categoryId()));
        item.setSourceSupplyItem(null);
        item.setDescription(request.description().trim());
        item.setQuantity(defaultDirectExpenseMaterialQuantity(request, invoiceType, categoryType));
        item.setUnit(request.unit());
        item.setUnitPrice(defaultDirectExpenseMaterialUnitPrice(request, invoiceType, categoryType));
        item.setTotalAmount(request.totalAmount());
    }

    private BigDecimal defaultDirectExpenseMaterialQuantity(InvoiceDtos.InvoiceItemUpsertRequest request,
                                                           InvoiceType invoiceType,
                                                           CategoryType categoryType) {
        if (request.quantity() == null && invoiceType == InvoiceType.DIRECT_EXPENSE && categoryType == CategoryType.MATERIAL) {
            return BigDecimal.ONE;
        }
        return request.quantity();
    }

    private BigDecimal defaultDirectExpenseMaterialUnitPrice(InvoiceDtos.InvoiceItemUpsertRequest request,
                                                            InvoiceType invoiceType,
                                                            CategoryType categoryType) {
        if (request.unitPrice() == null && invoiceType == InvoiceType.DIRECT_EXPENSE && categoryType == CategoryType.MATERIAL) {
            return request.totalAmount();
        }
        return request.unitPrice();
    }

    private void validateRegularItemRequest(InvoiceDtos.InvoiceItemUpsertRequest request) {
        if (request.categoryId() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "error.category.required", "Category is required");
        }
        if (request.description() == null || request.description().isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "error.invoice.item.description.required", "Item description is required");
        }
        if (request.totalAmount() == null || request.totalAmount().compareTo(BigDecimal.ZERO) < 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "error.invoice.item.total.required", "Item total amount is required");
        }
    }

    private BigDecimal sumItemTotals(List<SupplierInvoiceItem> items) {
        return items.stream().map(SupplierInvoiceItem::getTotalAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private void validateInvoiceTotal(SupplierInvoice invoice) {
        BigDecimal sum = invoiceItemRepository.sumTotalsByInvoiceIds(List.of(invoice.getId())).stream()
                .findFirst()
                .map(SupplierInvoiceItemRepository.InvoiceItemTotal::getTotalAmount)
                .orElse(BigDecimal.ZERO);
        if (sum.compareTo(invoice.getTotalAmount()) != 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "error.invoice.total.items-misaligned", "Invoice total must remain aligned with invoice items");
        }
    }

    private InvoiceDtos.InvoiceResponse toResponse(SupplierInvoice invoice) {
        return toResponses(List.of(invoice)).getFirst();
    }

    private List<InvoiceDtos.InvoiceResponse> toResponses(List<SupplierInvoice> invoices) {
        if (invoices.isEmpty()) {
            return List.of();
        }

        List<Long> invoiceIds = invoices.stream().map(SupplierInvoice::getId).toList();
        Map<Long, List<SupplierInvoiceItem>> itemsByInvoiceId = invoiceItemRepository.findDetailedByInvoiceIdIn(invoiceIds).stream()
                .collect(Collectors.groupingBy(item -> item.getInvoice().getId()));
        List<Long> supplyItemIds = itemsByInvoiceId.values().stream()
                .flatMap(Collection::stream)
                .filter(item -> item.getInvoice().getInvoiceType() == InvoiceType.SUPPLY)
                .map(SupplierInvoiceItem::getId)
                .distinct()
                .toList();
        Map<Long, SourceItemUsageAmounts> consumedBySourceItem = sourceItemUsageAmounts(supplyItemIds, List.of(InvoiceType.SUPPLY_USAGE), null);
        Map<Long, SourceItemUsageAmounts> outgoingBySourceItem = sourceItemUsageAmounts(supplyItemIds, List.of(InvoiceType.SUPPLY_USAGE, InvoiceType.SUPPLY_RETURN), null);

        return invoices.stream()
                .map(invoice -> toResponse(
                        invoice,
                        itemsByInvoiceId.getOrDefault(invoice.getId(), Collections.emptyList()),
                        consumedBySourceItem,
                        outgoingBySourceItem
                ))
                .toList();
    }

    private InvoiceDtos.InvoiceResponse toResponse(SupplierInvoice invoice,
                                                   List<SupplierInvoiceItem> items,
                                                   Map<Long, SourceItemUsageAmounts> consumedBySourceItem,
                                                   Map<Long, SourceItemUsageAmounts> outgoingBySourceItem) {
        BigDecimal consumedAmount = invoice.getInvoiceType() == InvoiceType.SUPPLY
                ? items.stream().map(item -> amountFor(item.getId(), consumedBySourceItem)).reduce(BigDecimal.ZERO, BigDecimal::add)
                : invoice.getTotalAmount();
        BigDecimal outgoingAmount = invoice.getInvoiceType() == InvoiceType.SUPPLY
                ? items.stream().map(item -> amountFor(item.getId(), outgoingBySourceItem)).reduce(BigDecimal.ZERO, BigDecimal::add)
                : invoice.getTotalAmount();
        List<InvoiceDtos.InvoiceItemResponse> itemResponses = items.stream()
                .map(item -> toItemResponse(item, consumedBySourceItem, outgoingBySourceItem))
                .toList();
        return new InvoiceDtos.InvoiceResponse(
                invoice.getId(),
                invoice.getInvoiceType(),
                invoice.getSupplier() != null ? invoice.getSupplier().getId() : null,
                invoice.getProject() == null ? null : invoice.getProject().getId(),
                invoice.getStage() == null ? null : invoice.getStage().getId(),
                invoice.getStage() == null ? null : invoice.getStage().getName(),
                invoice.getSourceSupplyInvoice() == null ? null : invoice.getSourceSupplyInvoice().getId(),
                invoice.getSourceSupplyInvoice() == null ? null : invoice.getSourceSupplyInvoice().getReference(),
                invoice.getSupplier() != null ? invoice.getSupplier().getName() : null,
                invoice.getReference(),
                invoice.getInvoiceDate(),
                invoice.getTotalAmount(),
                invoice.getCurrency(),
                invoice.getNotes(),
                invoice.getDocumentRef(),
                invoice.getStatus().name(),
                consumedAmount,
                invoice.getInvoiceType() == InvoiceType.SUPPLY ? invoice.getTotalAmount().subtract(outgoingAmount) : BigDecimal.ZERO,
                itemResponses
        );
    }

    private InvoiceDtos.InvoiceItemResponse toItemResponse(SupplierInvoiceItem item) {
        if (item.getInvoice().getInvoiceType() != InvoiceType.SUPPLY) {
            return toItemResponse(item, Collections.emptyMap(), Collections.emptyMap());
        }
        Map<Long, SourceItemUsageAmounts> consumedBySourceItem = sourceItemUsageAmounts(List.of(item.getId()), List.of(InvoiceType.SUPPLY_USAGE), null);
        Map<Long, SourceItemUsageAmounts> outgoingBySourceItem = sourceItemUsageAmounts(List.of(item.getId()), List.of(InvoiceType.SUPPLY_USAGE, InvoiceType.SUPPLY_RETURN), null);
        return toItemResponse(item, consumedBySourceItem, outgoingBySourceItem);
    }

    private InvoiceDtos.InvoiceItemResponse toItemResponse(SupplierInvoiceItem item,
                                                           Map<Long, SourceItemUsageAmounts> consumedBySourceItem,
                                                           Map<Long, SourceItemUsageAmounts> outgoingBySourceItem) {
        BigDecimal consumedAmount = item.getInvoice().getInvoiceType() == InvoiceType.SUPPLY
                ? amountFor(item.getId(), consumedBySourceItem)
                : BigDecimal.ZERO;
        BigDecimal outgoingAmount = item.getInvoice().getInvoiceType() == InvoiceType.SUPPLY
                ? amountFor(item.getId(), outgoingBySourceItem)
                : BigDecimal.ZERO;
        BigDecimal remainingAmount = item.getInvoice().getInvoiceType() == InvoiceType.SUPPLY
                ? item.getTotalAmount().subtract(outgoingAmount)
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
                item.getInvoice().getInvoiceType() == InvoiceType.SUPPLY
                        ? (item.getQuantity() == null ? BigDecimal.ZERO : item.getQuantity()).subtract(quantityFor(item.getId(), outgoingBySourceItem))
                        : null
        );
    }

    private Map<Long, SourceItemUsageAmounts> sourceItemUsageAmounts(Collection<Long> sourceItemIds,
                                                                     Collection<InvoiceType> invoiceTypes,
                                                                     Long excludingInvoiceId) {
        if (sourceItemIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return invoiceItemRepository.sumOutgoingBySourceItemIds(sourceItemIds, invoiceTypes, excludingInvoiceId).stream()
                .collect(Collectors.toMap(
                        SupplierInvoiceItemRepository.SourceItemUsageTotal::getSourceSupplyItemId,
                        row -> new SourceItemUsageAmounts(defaultZero(row.getTotalAmount()), defaultZero(row.getTotalQuantity()))
                ));
    }

    private BigDecimal amountFor(Long sourceItemId, Map<Long, SourceItemUsageAmounts> amountsBySourceItem) {
        return amountsBySourceItem.getOrDefault(sourceItemId, SourceItemUsageAmounts.ZERO).amount();
    }

    private BigDecimal quantityFor(Long sourceItemId, Map<Long, SourceItemUsageAmounts> amountsBySourceItem) {
        return amountsBySourceItem.getOrDefault(sourceItemId, SourceItemUsageAmounts.ZERO).quantity();
    }

    private BigDecimal defaultZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private void validateSupplySource(SupplierInvoice source) {
        if (source.getInvoiceType() != InvoiceType.SUPPLY) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "error.invoice.source-invoice.must-be-supply", "Source invoice must be a SUPPLY invoice");
        }
    }

    private ConstructionStage validateUsageStage(Long projectId, Long stageId) {
        ConstructionStage stage = referenceDataService.getStage(stageId);
        if (!stage.getProject().getId().equals(projectId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "error.stage.project-mismatch", "Stage must belong to the selected project");
        }
        return stage;
    }

    private List<SupplierInvoiceItem> buildUsageItems(SupplierInvoice usageInvoice,
                                                      SupplierInvoice source,
                                                      List<InvoiceDtos.InvoiceItemUpsertRequest> requests,
                                                      Long excludingInvoiceId) {
        if (requests == null || requests.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "error.invoice.usage.lines.required", "Usage invoice must contain at least one line");
        }
        requests.forEach(this::validateUsageItemRequest);

        List<Long> sourceItemIds = requests.stream().map(InvoiceDtos.InvoiceItemUpsertRequest::sourceSupplyItemId).distinct().toList();
        Map<Long, SupplierInvoiceItem> sourceItems = invoiceItemRepository.findByIdIn(sourceItemIds).stream()
                .collect(Collectors.toMap(SupplierInvoiceItem::getId, Function.identity()));
        List<SupplierInvoiceItem> usageItems = requests.stream()
                .filter(request -> request.quantity().compareTo(BigDecimal.ZERO) > 0)
                .map(request -> {
                    SupplierInvoiceItem sourceItem = sourceItems.get(request.sourceSupplyItemId());
                    if (sourceItem == null || !sourceItem.getInvoice().getId().equals(source.getId())) {
                        throw new ApiException(HttpStatus.BAD_REQUEST, "error.invoice.usage.line.source-invoice-mismatch", "Usage line must reference an item from the source SUPPLY invoice");
                    }
                    if (sourceItem.getQuantity() == null || sourceItem.getUnitPrice() == null) {
                        throw new ApiException(HttpStatus.BAD_REQUEST, "error.invoice.source-item.quantity-unit-price-required", "Source SUPPLY item must have quantity and unit price");
                    }
                    BigDecimal available = availableQuantity(sourceItem, excludingInvoiceId);
                    if (request.quantity().compareTo(available) > 0) {
                        throw new ApiException(HttpStatus.BAD_REQUEST, "error.invoice.usage.quantity.exceeds-available", "Usage quantity exceeds available supply quantity");
                    }
                    SupplierInvoiceItem item = new SupplierInvoiceItem();
                    item.setInvoice(usageInvoice);
                    item.setSourceSupplyItem(sourceItem);
                    item.setCategory(sourceItem.getCategory());
                    item.setDescription(sourceItem.getDescription());
                    item.setQuantity(request.quantity());
                    item.setUnit(sourceItem.getUnit());
                    item.setUnitPrice(sourceItem.getUnitPrice());
                    item.setTotalAmount(request.quantity().multiply(sourceItem.getUnitPrice()));
                    return item;
                })
                .toList();
        if (usageItems.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "error.invoice.usage.lines.positive-required", "Usage invoice must contain at least one positive line");
        }
        return usageItems;
    }

    private List<SupplierInvoiceItem> buildReturnItems(SupplierInvoice returnInvoice,
                                                       SupplierInvoice source,
                                                       List<InvoiceDtos.InvoiceItemUpsertRequest> requests,
                                                       Long excludingInvoiceId) {
        if (requests == null || requests.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "error.invoice.return.lines.required", "Return invoice must contain at least one line");
        }
        requests.forEach(this::validateReturnItemRequest);

        List<Long> sourceItemIds = requests.stream().map(InvoiceDtos.InvoiceItemUpsertRequest::sourceSupplyItemId).distinct().toList();
        Map<Long, SupplierInvoiceItem> sourceItems = invoiceItemRepository.findByIdIn(sourceItemIds).stream()
                .collect(Collectors.toMap(SupplierInvoiceItem::getId, Function.identity()));
        List<SupplierInvoiceItem> returnItems = requests.stream()
                .filter(request -> request.quantity().compareTo(BigDecimal.ZERO) > 0)
                .map(request -> {
                    SupplierInvoiceItem sourceItem = sourceItems.get(request.sourceSupplyItemId());
                    if (sourceItem == null || !sourceItem.getInvoice().getId().equals(source.getId())) {
                        throw new ApiException(HttpStatus.BAD_REQUEST, "error.invoice.return.line.source-invoice-mismatch", "Return line must reference an item from the source SUPPLY invoice");
                    }
                    if (sourceItem.getQuantity() == null || sourceItem.getUnitPrice() == null) {
                        throw new ApiException(HttpStatus.BAD_REQUEST, "error.invoice.source-item.quantity-unit-price-required", "Source SUPPLY item must have quantity and unit price");
                    }
                    BigDecimal available = availableQuantity(sourceItem, excludingInvoiceId);
                    if (request.quantity().compareTo(available) > 0) {
                        throw new ApiException(HttpStatus.BAD_REQUEST, "error.invoice.return.quantity.exceeds-available", "Return quantity exceeds available supply quantity");
                    }
                    SupplierInvoiceItem item = new SupplierInvoiceItem();
                    item.setInvoice(returnInvoice);
                    item.setSourceSupplyItem(sourceItem);
                    item.setCategory(sourceItem.getCategory());
                    item.setDescription(sourceItem.getDescription());
                    item.setQuantity(request.quantity());
                    item.setUnit(sourceItem.getUnit());
                    item.setUnitPrice(sourceItem.getUnitPrice());
                    item.setTotalAmount(request.quantity().multiply(sourceItem.getUnitPrice()));
                    return item;
                })
                .toList();
        if (returnItems.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "error.invoice.return.lines.positive-required", "Return invoice must contain at least one positive line");
        }
        return returnItems;
    }

    private void validateUsageItemRequest(InvoiceDtos.InvoiceItemUpsertRequest request) {
        if (request.sourceSupplyItemId() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "error.invoice.usage.line.source-item-required", "Usage line must reference a source supply item");
        }
        if (request.quantity() == null || request.quantity().compareTo(BigDecimal.ZERO) < 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "error.invoice.usage.quantity.required", "Usage quantity is required");
        }
    }

    private void validateReturnItemRequest(InvoiceDtos.InvoiceItemUpsertRequest request) {
        if (request.sourceSupplyItemId() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "error.invoice.return.line.source-item-required", "Return line must reference a source supply item");
        }
        if (request.quantity() == null || request.quantity().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "error.invoice.return.quantity.required", "Return quantity is required");
        }
    }

    private BigDecimal availableQuantity(SupplierInvoiceItem sourceItem, Long excludingInvoiceId) {
        BigDecimal initialQuantity = sourceItem.getQuantity() == null ? BigDecimal.ZERO : sourceItem.getQuantity();
        BigDecimal outgoingQuantity = outgoingAmounts(sourceItem.getId(), excludingInvoiceId).quantity();
        return initialQuantity.subtract(outgoingQuantity);
    }

    private SourceItemUsageAmounts outgoingAmounts(Long sourceItemId, Long excludingInvoiceId) {
        return sourceItemUsageAmounts(List.of(sourceItemId), List.of(InvoiceType.SUPPLY_USAGE, InvoiceType.SUPPLY_RETURN), excludingInvoiceId)
                .getOrDefault(sourceItemId, SourceItemUsageAmounts.ZERO);
    }

    private record SourceItemUsageAmounts(BigDecimal amount, BigDecimal quantity) {
        private static final SourceItemUsageAmounts ZERO = new SourceItemUsageAmounts(BigDecimal.ZERO, BigDecimal.ZERO);
    }

    private record ResolvedSupplyItemUpdate(SupplierInvoiceItem existingItem,
                                            InvoiceDtos.InvoiceItemUpsertRequest request) {
    }

    private String generateNotesFromRequests(List<InvoiceDtos.InvoiceItemUpsertRequest> items) {
        if (items == null) return null;
        String notes = items.stream()
                .map(item -> {
                    if (item.description() != null && !item.description().isBlank()) {
                        return item.description().trim();
                    }
                    if (item.categoryId() != null) {
                        return referenceDataService.getCategory(item.categoryId()).getName();
                    }
                    return null;
                })
                .filter(desc -> desc != null && !desc.isBlank())
                .distinct()
                .collect(Collectors.joining(" | "));
        return notes.isBlank() ? null : notes;
    }

    private String generateNotesFromItems(List<SupplierInvoiceItem> items) {
        if (items == null) return null;
        String notes = items.stream()
                .map(SupplierInvoiceItem::getDescription)
                .filter(desc -> desc != null && !desc.isBlank())
                .distinct()
                .collect(Collectors.joining(" | "));
        return notes.isBlank() ? null : notes;
    }
}
