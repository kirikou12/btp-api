package mr.btp.api.consumption;

import mr.btp.api.common.exception.ApiException;
import mr.btp.api.common.service.ReferenceDataService;
import mr.btp.api.invoice.SupplierInvoiceItem;
import mr.btp.api.project.ConstructionStage;
import mr.btp.api.project.StageStatus;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
public class MaterialConsumptionService {

    private final MaterialConsumptionRepository consumptionRepository;
    private final ReferenceDataService referenceDataService;

    public MaterialConsumptionService(MaterialConsumptionRepository consumptionRepository,
                                      ReferenceDataService referenceDataService) {
        this.consumptionRepository = consumptionRepository;
        this.referenceDataService = referenceDataService;
    }

    @Transactional(readOnly = true)
    public List<ConsumptionDtos.ConsumptionResponse> byProject(Long projectId) {
        referenceDataService.getProject(projectId);
        return referenceDataService.consumptionsByProject(projectId).stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public ConsumptionDtos.ConsumptionResponse get(Long id) {
        return toResponse(referenceDataService.getConsumption(id));
    }

    @Transactional
    public ConsumptionDtos.ConsumptionResponse create(ConsumptionDtos.ConsumptionRequest request) {
        MaterialConsumption consumption = new MaterialConsumption();
        apply(consumption, request, null);
        return toResponse(consumptionRepository.save(consumption));
    }

    @Transactional
    public ConsumptionDtos.ConsumptionResponse update(Long id, ConsumptionDtos.ConsumptionRequest request) {
        MaterialConsumption consumption = referenceDataService.getConsumption(id);
        apply(consumption, request, id);
        return toResponse(consumptionRepository.save(consumption));
    }

    @Transactional
    public void delete(Long id) {
        if (!consumptionRepository.existsById(id)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Material consumption not found");
        }
        consumptionRepository.deleteById(id);
    }

    private void apply(MaterialConsumption consumption, ConsumptionDtos.ConsumptionRequest request, Long existingId) {
        SupplierInvoiceItem invoiceItem = referenceDataService.getInvoiceItem(request.invoiceItemId());
        ConstructionStage stage = referenceDataService.getStage(request.stageId());
        if (!stage.getProject().getId().equals(request.projectId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Stage must belong to the selected project");
        }
        if (stage.getStatus() == StageStatus.COMPLETED) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Cannot record consumption on a completed stage");
        }
        if (!invoiceItem.getCategory().getId().equals(request.categoryId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Consumption category must match the invoice item category");
        }
        BigDecimal consumedWithoutCurrent = referenceDataService.invoiceItemConsumedAmount(invoiceItem.getId(), existingId);
        BigDecimal remaining = invoiceItem.getTotalAmount().subtract(consumedWithoutCurrent);
        if (request.amountUsed().compareTo(remaining) > 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Consumption exceeds remaining invoice item balance");
        }
        consumption.setInvoiceItem(invoiceItem);
        consumption.setProject(referenceDataService.getProject(request.projectId()));
        consumption.setStage(stage);
        consumption.setCategory(referenceDataService.getCategory(request.categoryId()));
        consumption.setQuantityUsed(request.quantityUsed());
        consumption.setAmountUsed(request.amountUsed());
        consumption.setConsumptionDate(request.consumptionDate());
        consumption.setNotes(request.notes());
    }

    private ConsumptionDtos.ConsumptionResponse toResponse(MaterialConsumption consumption) {
        return new ConsumptionDtos.ConsumptionResponse(
                consumption.getId(),
                consumption.getInvoiceItem().getId(),
                consumption.getProject().getId(),
                consumption.getStage().getId(),
                consumption.getCategory().getId(),
                consumption.getCategory().getName(),
                consumption.getQuantityUsed(),
                consumption.getAmountUsed(),
                consumption.getConsumptionDate(),
                consumption.getNotes()
        );
    }
}
