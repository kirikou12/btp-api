package mr.btp.api;

import mr.btp.api.document.DocumentStorageService;
import mr.btp.api.consumption.ConsumptionDtos;
import mr.btp.api.consumption.MaterialConsumptionService;
import mr.btp.api.invoice.InvoiceDtos;
import mr.btp.api.invoice.InvoiceService;
import mr.btp.api.invoice.InvoiceStatus;
import mr.btp.api.invoice.InvoiceType;
import mr.btp.api.project.ConstructionStage;
import mr.btp.api.project.ConstructionStageRepository;
import mr.btp.api.project.StageStatus;
import mr.btp.api.worker.WorkerDtos;
import mr.btp.api.worker.WorkerService;
import mr.btp.api.worker.WorkerType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class RimBtpApiApplicationTests {

    @Autowired
    private MaterialConsumptionService materialConsumptionService;

    @Autowired
    private WorkerService workerService;

    @Autowired
    private InvoiceService invoiceService;

    @Autowired
    private ConstructionStageRepository stageRepository;

    @Autowired
    private DocumentStorageService documentStorageService;

    @Autowired
    private MockMvc mockMvc;

    @Test
    @Transactional
    void shouldCreateUsageInvoiceWithMultipleLinesAndReduceSupplyAvailability() {
        InvoiceDtos.InvoiceResponse supply = invoiceService.list(0, 50, "SUPPLY").content().stream()
                .filter(candidate -> candidate.projectId() != null)
                .filter(candidate -> candidate.items().stream().filter(item -> item.availableQuantity().compareTo(BigDecimal.ONE) >= 0).count() >= 2)
                .findFirst()
                .orElseThrow();
        ConstructionStage activeStage = activeStageForProject(supply.projectId());

        InvoiceDtos.InvoiceResponse usage = invoiceService.createUsage(new InvoiceDtos.UsageInvoiceRequest(
                supply.id(),
                supply.projectId(),
                activeStage.getId(),
                LocalDate.now(),
                "Two material usage",
                null,
                InvoiceStatus.CONFIRMED,
                supply.items().stream()
                        .filter(item -> item.availableQuantity().compareTo(BigDecimal.ONE) >= 0)
                        .limit(2)
                        .map(item -> new InvoiceDtos.UsageInvoiceItemRequest(item.id(), BigDecimal.ONE))
                        .toList()
        ));

        assertThat(usage.invoiceType()).isEqualTo(InvoiceType.SUPPLY_USAGE);
        assertThat(usage.sourceSupplyInvoiceId()).isEqualTo(supply.id());
        assertThat(usage.stageId()).isEqualTo(activeStage.getId());
        assertThat(usage.items()).hasSize(2);
        assertThat(usage.totalAmount()).isEqualByComparingTo(
                usage.items().stream().map(InvoiceDtos.InvoiceItemResponse::unitPrice).reduce(BigDecimal.ZERO, BigDecimal::add)
        );

        InvoiceDtos.InvoiceResponse refreshedSupply = invoiceService.get(supply.id());
        usage.items().forEach(usageItem -> {
            InvoiceDtos.InvoiceItemResponse before = supply.items().stream()
                    .filter(item -> item.id().equals(usageItem.sourceSupplyItemId()))
                    .findFirst()
                    .orElseThrow();
            InvoiceDtos.InvoiceItemResponse after = refreshedSupply.items().stream()
                    .filter(item -> item.id().equals(usageItem.sourceSupplyItemId()))
                    .findFirst()
                    .orElseThrow();
            assertThat(after.availableQuantity()).isEqualByComparingTo(before.availableQuantity().subtract(BigDecimal.ONE));
        });
    }

    @Test
    @Transactional
    void shouldCreateDirectUsageInvoiceWithoutSourceSupply() {
        InvoiceDtos.InvoiceResponse supply = invoiceService.list(0, 50, "SUPPLY").content().stream()
                .filter(candidate -> candidate.projectId() != null)
                .filter(candidate -> !candidate.items().isEmpty())
                .findFirst()
                .orElseThrow();
        ConstructionStage activeStage = activeStageForProject(supply.projectId());
        InvoiceDtos.InvoiceItemResponse material = supply.items().getFirst();

        InvoiceDtos.InvoiceResponse direct = invoiceService.create(new InvoiceDtos.InvoiceRequest(
                InvoiceType.DIRECT_USAGE,
                supply.supplierId(),
                supply.projectId(),
                activeStage.getId(),
                "DIRECT-TEST",
                LocalDate.now(),
                new BigDecimal("125.00"),
                supply.currency(),
                "Billed and consumed immediately",
                null,
                InvoiceStatus.CONFIRMED,
                java.util.List.of(new InvoiceDtos.InvoiceItemUpsertRequest(
                        null,
                        null,
                        material.categoryId(),
                        material.description(),
                        BigDecimal.ONE,
                        material.unit(),
                        new BigDecimal("125.00"),
                        new BigDecimal("125.00")
                ))
        ));

        assertThat(direct.invoiceType()).isEqualTo(InvoiceType.DIRECT_USAGE);
        assertThat(direct.sourceSupplyInvoiceId()).isNull();
        assertThat(direct.stageId()).isEqualTo(activeStage.getId());
        assertThat(direct.consumedAmount()).isEqualByComparingTo("125.00");
        assertThat(direct.remainingAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(invoiceService.usageInvoicesByProject(supply.projectId(), activeStage.getId()))
                .extracting(InvoiceDtos.InvoiceResponse::id)
                .contains(direct.id());
    }

    @Test
    @Transactional
    void shouldRejectUsageInvoiceAsSource() {
        InvoiceDtos.InvoiceResponse source = invoiceService.list(0, 50, "SUPPLY").content().stream()
                .filter(candidate -> candidate.projectId() != null)
                .filter(candidate -> candidate.items().stream().anyMatch(item -> item.availableQuantity().compareTo(BigDecimal.ONE) >= 0))
                .findFirst()
                .orElseThrow();
        ConstructionStage activeStage = activeStageForProject(source.projectId());
        InvoiceDtos.InvoiceItemResponse sourceItem = source.items().stream()
                .filter(item -> item.availableQuantity().compareTo(BigDecimal.ONE) >= 0)
                .findFirst()
                .orElseThrow();
        InvoiceDtos.InvoiceResponse usage = invoiceService.createUsage(new InvoiceDtos.UsageInvoiceRequest(
                source.id(),
                source.projectId(),
                activeStage.getId(),
                LocalDate.now(),
                "Usage source guard setup",
                null,
                InvoiceStatus.CONFIRMED,
                java.util.List.of(new InvoiceDtos.UsageInvoiceItemRequest(sourceItem.id(), BigDecimal.ONE))
        ));

        assertThatThrownBy(() -> invoiceService.createUsage(new InvoiceDtos.UsageInvoiceRequest(
                usage.id(),
                source.projectId(),
                activeStage.getId(),
                LocalDate.now(),
                "Wrong source invoice",
                null,
                InvoiceStatus.CONFIRMED,
                java.util.List.of(new InvoiceDtos.UsageInvoiceItemRequest(sourceItem.id(), BigDecimal.ONE))
        ))).hasMessageContaining("SUPPLY invoice");
    }

    @Test
    void shouldPreventOverConsumption() {
        InvoiceDtos.InvoiceResponse invoice = invoiceService.list(0, 20).content().stream()
                .filter(candidate -> candidate.projectId() != null && !candidate.items().isEmpty())
                .findFirst()
                .orElseThrow();
        InvoiceDtos.InvoiceItemResponse item = invoice.items().getFirst();
        ConstructionStage activeStage = stageByName(invoice.projectId(), "Elevation");

        ConsumptionDtos.ConsumptionRequest request = new ConsumptionDtos.ConsumptionRequest(
                item.id(),
                invoice.projectId(),
                activeStage.getId(),
                item.categoryId(),
                new BigDecimal("1.00"),
                item.remainingAmount().add(new BigDecimal("1.00")),
                LocalDate.now(),
                "Too much"
        );

        assertThatThrownBy(() -> materialConsumptionService.create(request))
                .hasMessageContaining("exceeds remaining invoice item balance");
    }

    @Test
    void shouldLoadSupplierInvoicesWithMaterialCategoryNames() {
        var page = invoiceService.list(0, 20);

        assertThat(page.content()).isNotEmpty();
        InvoiceDtos.InvoiceResponse invoice = page.content().getFirst();
        assertThat(invoice.items()).isNotEmpty();
        assertThat(invoice.items().getFirst().categoryName()).isNotBlank();

        InvoiceDtos.InvoiceResponse sameInvoice = invoiceService.get(invoice.id());
        assertThat(sameInvoice.items()).isNotEmpty();
        assertThat(sameInvoice.items().getFirst().categoryName()).isNotBlank();
    }

    @Test
    @Transactional
    void shouldCreateAndUpdateWorkerProjectAssignment() {
        ConstructionStage stage = stageRepository.findAll().stream().findFirst().orElseThrow();
        Long projectId = stage.getProject().getId();

        WorkerDtos.WorkerResponse created = workerService.createWorker(new WorkerDtos.WorkerRequest(
                "Project mason",
                WorkerType.MASON,
                projectId,
                new BigDecimal("1500.00")
        ));

        assertThat(created.projectId()).isEqualTo(projectId);

        WorkerDtos.WorkerResponse updated = workerService.updateWorker(created.id(), new WorkerDtos.WorkerRequest(
                "Project mason",
                WorkerType.MASON,
                null,
                new BigDecimal("1750.00")
        ));

        assertThat(updated.projectId()).isNull();
        assertThat(updated.plannedBudget()).isEqualByComparingTo("1750.00");
    }

    @Test
    @Transactional
    void shouldAllowUpdatingWorkerPaymentOnCompletedStageForCorrections() {
        ConstructionStage completedStage = stageByName("Demo depenses CSV", "Fondation");
        assertThat(completedStage.getStatus()).isEqualTo(StageStatus.COMPLETED);

        WorkerDtos.WorkerPaymentResponse existingPayment = workerService.paymentsByProject(completedStage.getProject().getId()).stream()
                .filter(payment -> completedStage.getId().equals(payment.stageId()))
                .findFirst()
                .orElseThrow();

        WorkerDtos.WorkerPaymentResponse updatedPayment = workerService.updatePayment(
                existingPayment.id(),
                new WorkerDtos.WorkerPaymentRequest(
                        existingPayment.workerId(),
                        existingPayment.projectId(),
                        existingPayment.stageId(),
                        new BigDecimal("2100.00"),
                        existingPayment.paymentDate(),
                        existingPayment.documentRef()
                )
        );

        assertThat(updatedPayment.amount()).isEqualByComparingTo("2100.00");
        assertThat(updatedPayment.workerName()).isEqualTo(existingPayment.workerName());
    }

    @Test
    @Transactional
    void shouldAllowUpdatingConsumptionOnCompletedStageForCorrections() {
        ConstructionStage completedStage = stageByName("Demo depenses CSV", "Fondation");
        assertThat(completedStage.getStatus()).isEqualTo(StageStatus.COMPLETED);

        ConsumptionDtos.ConsumptionResponse existingConsumption = materialConsumptionService.byProject(completedStage.getProject().getId()).stream()
                .filter(consumption -> completedStage.getId().equals(consumption.stageId()))
                .findFirst()
                .orElseThrow();

        ConsumptionDtos.ConsumptionResponse updatedConsumption = materialConsumptionService.update(
                existingConsumption.id(),
                new ConsumptionDtos.ConsumptionRequest(
                        existingConsumption.invoiceItemId(),
                        existingConsumption.projectId(),
                        existingConsumption.stageId(),
                        existingConsumption.categoryId(),
                        existingConsumption.quantityUsed(),
                        new BigDecimal("450.00"),
                        existingConsumption.consumptionDate(),
                        "Foundation slab corrected"
                )
        );

        assertThat(updatedConsumption.amountUsed()).isEqualByComparingTo("450.00");
        assertThat(updatedConsumption.notes()).contains("corrected");
    }

    @Test
    @Transactional
    void shouldStoreAndLoadUploadedDocument() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "proof.jpg",
                "image/jpeg",
                new byte[]{1, 2, 3, 4}
        );

        DocumentStorageService.DocumentUploadResponse uploaded = documentStorageService.storeImage(file);

        assertThat(uploaded.path()).startsWith("/api/uploads/");
        DocumentStorageService.StoredDocument storedDocument = documentStorageService.load(uploaded.fileName());
        assertThat(storedDocument.contentType()).isEqualTo("image/jpeg");
        assertThat(storedDocument.size()).isEqualTo(4);
        assertThat(storedDocument.content()).containsExactly(1, 2, 3, 4);
    }

    @Test
    void shouldExposeActuatorLivenessWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/actuator/health/liveness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    private ConstructionStage stageByName(String projectName, String stageName) {
        return stageRepository.findAll().stream()
                .filter(stage -> projectName.equals(stage.getProject().getName()))
                .filter(stage -> stageName.equals(stage.getName()))
                .findFirst()
                .orElseThrow();
    }

    private ConstructionStage stageByName(Long projectId, String stageName) {
        return stageRepository.findByProjectIdOrderBySortOrderAsc(projectId).stream()
                .filter(stage -> stageName.equals(stage.getName()))
                .findFirst()
                .orElseThrow();
    }

    private ConstructionStage activeStageForProject(Long projectId) {
        return stageRepository.findByProjectIdOrderBySortOrderAsc(projectId).stream()
                .filter(stage -> stage.getStatus() != StageStatus.COMPLETED)
                .findFirst()
                .orElseThrow();
    }
}
