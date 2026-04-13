package mr.btp.api;

import mr.btp.api.document.DocumentStorageService;
import mr.btp.api.consumption.ConsumptionDtos;
import mr.btp.api.consumption.MaterialConsumptionService;
import mr.btp.api.invoice.InvoiceDtos;
import mr.btp.api.invoice.InvoiceService;
import mr.btp.api.project.ConstructionStage;
import mr.btp.api.project.ConstructionStageRepository;
import mr.btp.api.project.StageStatus;
import mr.btp.api.worker.WorkerDtos;
import mr.btp.api.worker.WorkerService;
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
}
