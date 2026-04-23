package mr.btp.api;

import mr.btp.api.document.DocumentStorageService;
import mr.btp.api.dashboard.DashboardDtos;
import mr.btp.api.dashboard.DashboardService;
import mr.btp.api.invoice.InvoiceDtos;
import mr.btp.api.invoice.InvoiceService;
import mr.btp.api.invoice.InvoiceStatus;
import mr.btp.api.invoice.InvoiceType;
import mr.btp.api.project.ConstructionStage;
import mr.btp.api.project.ConstructionStageRepository;
import mr.btp.api.project.Project;
import mr.btp.api.project.ProjectRepository;
import mr.btp.api.project.ProjectStatus;
import mr.btp.api.project.StageStatus;
import mr.btp.api.supplier.SupplierDtos;
import mr.btp.api.supplier.SupplierService;
import mr.btp.api.worker.WorkerDtos;
import mr.btp.api.worker.WorkerService;
import mr.btp.api.worker.WorkerType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class RimBtpApiApplicationTests {

    @Autowired
    private WorkerService workerService;

    @Autowired
    private InvoiceService invoiceService;

    @Autowired
    private SupplierService supplierService;

    @Autowired
    private DashboardService dashboardService;

    @Autowired
    private ConstructionStageRepository stageRepository;

    @Autowired
    private ProjectRepository projectRepository;

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

        InvoiceDtos.InvoiceResponse usage = invoiceService.create(new InvoiceDtos.InvoiceRequest(
                InvoiceType.SUPPLY_USAGE,
                null,
                supply.projectId(),
                activeStage.getId(),
                supply.id(),
                null,
                LocalDate.now(),
                null,
                null,
                "Two material usage",
                null,
                InvoiceStatus.CONFIRMED,
                supply.items().stream()
                        .filter(item -> item.availableQuantity().compareTo(BigDecimal.ONE) >= 0)
                        .limit(2)
                        .map(item -> new InvoiceDtos.InvoiceItemUpsertRequest(null, item.id(), null, null, BigDecimal.ONE, null, null, null))
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
                null,
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
    void shouldCreateSupplyReturnAndReduceAvailableQuantity() {
        InvoiceDtos.InvoiceResponse supply = createTestSupply(new BigDecimal("10.00"));
        InvoiceDtos.InvoiceItemResponse sourceItem = supply.items().getFirst();

        InvoiceDtos.InvoiceResponse supplyReturn = createReturn(supply, sourceItem, BigDecimal.ONE);

        assertThat(supplyReturn.invoiceType()).isEqualTo(InvoiceType.SUPPLY_RETURN);
        assertThat(supplyReturn.sourceSupplyInvoiceId()).isEqualTo(supply.id());
        assertThat(supplyReturn.projectId()).isEqualTo(supply.projectId());
        assertThat(supplyReturn.stageId()).isNull();

        InvoiceDtos.InvoiceResponse refreshedSupply = invoiceService.get(supply.id());
        assertThat(refreshedSupply.items().getFirst().availableQuantity()).isEqualByComparingTo("9.00");
        assertThat(refreshedSupply.items().getFirst().consumedAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(refreshedSupply.items().getFirst().remainingAmount()).isEqualByComparingTo(new BigDecimal("900.00"));
    }

    @Test
    @Transactional
    void shouldRejectSupplyReturnAboveAvailableQuantity() {
        InvoiceDtos.InvoiceResponse supply = createTestSupply(new BigDecimal("10.00"));
        InvoiceDtos.InvoiceItemResponse sourceItem = supply.items().getFirst();

        assertThatThrownBy(() -> createReturn(supply, sourceItem, new BigDecimal("11.00")))
                .hasMessageContaining("Return quantity exceeds available supply quantity");
    }

    @Test
    @Transactional
    void shouldPreventUsageOfReturnedQuantities() {
        InvoiceDtos.InvoiceResponse supply = createTestSupply(new BigDecimal("10.00"));
        InvoiceDtos.InvoiceItemResponse sourceItem = supply.items().getFirst();
        ConstructionStage activeStage = activeStageForProject(supply.projectId());

        createReturn(supply, sourceItem, new BigDecimal("4.00"));

        InvoiceDtos.InvoiceResponse usage = invoiceService.createUsage(new InvoiceDtos.UsageInvoiceRequest(
                supply.id(),
                supply.projectId(),
                activeStage.getId(),
                LocalDate.now(),
                "Use remaining after return",
                null,
                InvoiceStatus.CONFIRMED,
                java.util.List.of(new InvoiceDtos.UsageInvoiceItemRequest(sourceItem.id(), new BigDecimal("6.00")))
        ));
        assertThat(usage.totalAmount()).isEqualByComparingTo("600.00");
        assertThat(invoiceService.get(supply.id()).items().getFirst().availableQuantity()).isEqualByComparingTo(BigDecimal.ZERO);

        assertThatThrownBy(() -> invoiceService.createUsage(new InvoiceDtos.UsageInvoiceRequest(
                supply.id(),
                supply.projectId(),
                activeStage.getId(),
                LocalDate.now(),
                "One more should fail",
                null,
                InvoiceStatus.CONFIRMED,
                java.util.List.of(new InvoiceDtos.UsageInvoiceItemRequest(sourceItem.id(), BigDecimal.ONE))
        ))).hasMessageContaining("Usage quantity exceeds available supply quantity");
    }

    @Test
    @Transactional
    void shouldNotCountSupplyReturnAsProjectCost() {
        InvoiceDtos.InvoiceResponse supply = createTestSupply(new BigDecimal("10.00"));
        InvoiceDtos.InvoiceItemResponse sourceItem = supply.items().getFirst();
        DashboardDtos.DashboardResponse beforeProject = dashboardService.byProject(supply.projectId());
        DashboardDtos.DashboardResponse beforeGlobal = dashboardService.global();

        InvoiceDtos.InvoiceResponse supplyReturn = createReturn(supply, sourceItem, new BigDecimal("3.00"));

        DashboardDtos.DashboardResponse afterProject = dashboardService.byProject(supply.projectId());
        DashboardDtos.DashboardResponse afterGlobal = dashboardService.global();
        assertThat(afterProject.totalActualCost()).isEqualByComparingTo(beforeProject.totalActualCost());
        assertThat(afterProject.totalMaterialConsumed()).isEqualByComparingTo(beforeProject.totalMaterialConsumed());
        assertThat(afterGlobal.materialsRemainingWithSuppliers())
                .isEqualByComparingTo(beforeGlobal.materialsRemainingWithSuppliers().subtract(supplyReturn.totalAmount()));
    }

    @Test
    @Transactional
    void shouldUpdateSupplyReturnAndValidateEditableAvailability() {
        InvoiceDtos.InvoiceResponse supply = createTestSupply(new BigDecimal("10.00"));
        InvoiceDtos.InvoiceItemResponse sourceItem = supply.items().getFirst();
        InvoiceDtos.InvoiceResponse supplyReturn = createReturn(supply, sourceItem, new BigDecimal("2.00"));

        InvoiceDtos.InvoiceResponse updated = invoiceService.update(supplyReturn.id(), new InvoiceDtos.InvoiceRequest(
                InvoiceType.SUPPLY_RETURN,
                null,
                null,
                null,
                supply.id(),
                "RETURN-UPDATED",
                LocalDate.now(),
                null,
                null,
                "Return updated",
                null,
                InvoiceStatus.CONFIRMED,
                java.util.List.of(new InvoiceDtos.InvoiceItemUpsertRequest(null, sourceItem.id(), null, null, new BigDecimal("3.00"), null, null, null))
        ));

        assertThat(updated.totalAmount()).isEqualByComparingTo("300.00");
        assertThat(invoiceService.get(supply.id()).items().getFirst().availableQuantity()).isEqualByComparingTo("7.00");

        assertThatThrownBy(() -> invoiceService.update(supplyReturn.id(), new InvoiceDtos.InvoiceRequest(
                InvoiceType.SUPPLY_RETURN,
                null,
                null,
                null,
                supply.id(),
                "RETURN-TOO-MUCH",
                LocalDate.now(),
                null,
                null,
                "Return too much",
                null,
                InvoiceStatus.CONFIRMED,
                java.util.List.of(new InvoiceDtos.InvoiceItemUpsertRequest(null, sourceItem.id(), null, null, new BigDecimal("11.00"), null, null, null))
        ))).hasMessageContaining("Return quantity exceeds available supply quantity");
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
    @WithMockUser
    void shouldDeleteSupplierWithoutInvoices() throws Exception {
        SupplierDtos.SupplierResponse supplier = supplierService.create(new SupplierDtos.SupplierRequest(
                "Delete me",
                null,
                null,
                null,
                null
        ));

        mockMvc.perform(delete("/api/suppliers/{id}", supplier.id()))
                .andExpect(status().isOk());

        assertThatThrownBy(() -> supplierService.get(supplier.id()))
                .hasMessageContaining("Supplier not found");
    }

    @Test
    @Transactional
    @WithMockUser
    void shouldRejectDeletingSupplierWithInvoices() throws Exception {
        InvoiceDtos.InvoiceResponse supply = createTestSupply(new BigDecimal("10.00"));

        mockMvc.perform(delete("/api/suppliers/{id}", supply.supplierId()))
                .andExpect(status().isConflict());

        assertThat(supplierService.get(supply.supplierId()).id()).isEqualTo(supply.supplierId());
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
                null,
                new BigDecimal("1500.00"),
                null
        ));

        assertThat(created.projectId()).isEqualTo(projectId);
        assertThat(created.projectIds()).contains(projectId);

        WorkerDtos.WorkerResponse updated = workerService.updateWorker(created.id(), new WorkerDtos.WorkerRequest(
                "Project mason",
                WorkerType.MASON,
                null,
                null,
                new BigDecimal("1750.00"),
                null
        ));

        assertThat(updated.projectId()).isNull();
        assertThat(updated.projectIds()).isEmpty();
        assertThat(updated.plannedBudget()).isEqualByComparingTo("1750.00");
    }

    @Test
    @Transactional
    void shouldAttachWorkerToMultipleProjects() {
        ConstructionStage firstStage = stageRepository.findAll().stream().findFirst().orElseThrow();
        Project secondProject = new Project();
        secondProject.setName("Second worker project");
        secondProject.setLocation("Nouakchott");
        secondProject.setDescription("Project used for worker membership regression");
        secondProject.setStartDate(LocalDate.now());
        secondProject.setEstimatedSalePrice(new BigDecimal("10000.00"));
        secondProject.setBudget(new BigDecimal("8000.00"));
        secondProject.setStatus(ProjectStatus.PLANNING);
        secondProject = projectRepository.save(secondProject);

        ConstructionStage secondStage = new ConstructionStage();
        secondStage.setProject(secondProject);
        secondStage.setName("Second stage");
        secondStage.setSortOrder(1);
        secondStage.setStatus(StageStatus.NOT_STARTED);
        secondStage.setPlannedBudget(new BigDecimal("1000.00"));
        secondStage.setProgressPercent(0);
        stageRepository.save(secondStage);

        List<Long> projectIds = List.of(firstStage.getProject().getId(), secondProject.getId());

        WorkerDtos.WorkerResponse created = workerService.createWorker(new WorkerDtos.WorkerRequest(
                "Shared mason",
                WorkerType.MASON,
                projectIds.getFirst(),
                null,
                new BigDecimal("1500.00"),
                null
        ));

        WorkerDtos.WorkerResponse updated = workerService.updateWorker(created.id(), new WorkerDtos.WorkerRequest(
                "Shared mason",
                WorkerType.MASON,
                projectIds.getFirst(),
                projectIds,
                new BigDecimal("1500.00"),
                null
        ));

        assertThat(updated.projectIds()).containsExactlyInAnyOrderElementsOf(projectIds);
        assertThat(workerService.listWorkers(projectIds.getFirst())).extracting(WorkerDtos.WorkerResponse::id).contains(updated.id());
        assertThat(workerService.listWorkers(projectIds.get(1))).extracting(WorkerDtos.WorkerResponse::id).contains(updated.id());
    }

    @Test
    @Transactional
    void shouldDistributeWorkerBudgetByProjectStages() {
        ConstructionStage firstStage = stageRepository.findAll().stream().findFirst().orElseThrow();
        List<ConstructionStage> projectStages = stageRepository.findByProjectIdOrderBySortOrderAsc(firstStage.getProject().getId());

        WorkerDtos.WorkerResponse created = workerService.createWorker(new WorkerDtos.WorkerRequest(
                "Distributed mason",
                WorkerType.MASON,
                firstStage.getProject().getId(),
                null,
                new BigDecimal("0.00"),
                projectStages.stream()
                        .limit(2)
                        .map(stage -> new WorkerDtos.WorkerStageBudgetRequest(stage.getId(), new BigDecimal("500.00")))
                        .toList()
        ));

        assertThat(created.plannedBudget()).isEqualByComparingTo("1000.00");
        assertThat(created.stageBudgets()).hasSize(2);
        assertThat(created.stageBudgets()).extracting(WorkerDtos.WorkerStageBudgetResponse::stageId)
                .containsExactlyElementsOf(projectStages.stream().limit(2).map(ConstructionStage::getId).toList());

        WorkerDtos.WorkerResponse updated = workerService.updateWorker(created.id(), new WorkerDtos.WorkerRequest(
                "Distributed mason",
                WorkerType.MASON,
                firstStage.getProject().getId(),
                null,
                new BigDecimal("0.00"),
                projectStages.stream()
                        .limit(2)
                        .map(stage -> new WorkerDtos.WorkerStageBudgetRequest(stage.getId(), new BigDecimal("600.00")))
                        .toList()
        ));

        assertThat(updated.plannedBudget()).isEqualByComparingTo("1200.00");
        assertThat(updated.stageBudgets()).extracting(WorkerDtos.WorkerStageBudgetResponse::plannedBudget)
                .containsExactly(new BigDecimal("600.00"), new BigDecimal("600.00"));
    }

    @Test
    @Transactional
    void shouldAllowCreatingWorkerPaymentOnCompletedStageWhenUserContinuesAnyway() {
        ConstructionStage completedStage = stageByName("Demo depenses CSV", "Fondation");
        assertThat(completedStage.getStatus()).isEqualTo(StageStatus.COMPLETED);

        WorkerDtos.WorkerResponse worker = workerService.listWorkers(completedStage.getProject().getId()).stream()
                .findFirst()
                .orElseThrow();

        WorkerDtos.WorkerPaymentResponse payment = workerService.createPayment(new WorkerDtos.WorkerPaymentRequest(
                worker.id(),
                completedStage.getProject().getId(),
                completedStage.getId(),
                new BigDecimal("125.00"),
                LocalDate.now(),
                "late-receipt.jpg"
        ));

        assertThat(payment.stageId()).isEqualTo(completedStage.getId());
        assertThat(payment.amount()).isEqualByComparingTo("125.00");
        assertThat(payment.documentRef()).isEqualTo("late-receipt.jpg");
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
    @Transactional
    void shouldHandleVariousImageContentTypes() {
        String[] types = {"image/jpg", "image/pjpeg", "image/heic-sequence", "image/heif-sequence"};
        for (String type : types) {
            MockMultipartFile file = new MockMultipartFile(
                    "file",
                    "photo",
                    type,
                    new byte[]{1, 2, 3, 4}
            );
            DocumentStorageService.DocumentUploadResponse uploaded = documentStorageService.storeImage(file);
            assertThat(uploaded.contentType()).isIn("image/jpeg", "image/heic");
        }
    }

    @Autowired
    private mr.btp.api.category.ExpenseCategoryRepository categoryRepository;

    @Test
    @Transactional
    void shouldCreateDirectExpenseInvoiceWithoutSupplierAndDefaultMaterialQuantity() {
        Project project = projectRepository.findAll().getFirst();
        ConstructionStage stage = activeStageForProject(project.getId());
        mr.btp.api.category.ExpenseCategory category = categoryRepository.findAll().stream()
                .filter(c -> c.getType() == mr.btp.api.category.CategoryType.MATERIAL)
                .findFirst().orElseThrow();

        InvoiceDtos.InvoiceResponse response = invoiceService.create(new InvoiceDtos.InvoiceRequest(
                InvoiceType.DIRECT_EXPENSE,
                null,
                project.getId(),
                stage.getId(),
                null,
                "DIRECT-TEST",
                LocalDate.now(),
                new BigDecimal("100.00"),
                "MRU",
                "Direct material expense",
                "proof.jpg",
                InvoiceStatus.CONFIRMED,
                List.of(new InvoiceDtos.InvoiceItemUpsertRequest(
                        null,
                        null,
                        category.getId(),
                        "Direct material expense",
                        null,
                        null,
                        null,
                        new BigDecimal("100.00")
                ))
        ));

        assertThat(response.invoiceType()).isEqualTo(InvoiceType.DIRECT_EXPENSE);
        assertThat(response.supplierId()).isNull();
        assertThat(response.invoiceDate()).isEqualTo(LocalDate.now());
        assertThat(response.documentRef()).isEqualTo("proof.jpg");
        assertThat(response.items()).singleElement().satisfies(item -> {
            assertThat(item.quantity()).isEqualByComparingTo(BigDecimal.ONE);
            assertThat(item.unitPrice()).isEqualByComparingTo("100.00");
        });
    }

    @Test
    @Transactional
    void shouldAllowCreatingDirectExpenseInvoiceOnCompletedStageWhenUserContinuesAnyway() {
        ConstructionStage completedStage = stageByName("Demo depenses CSV", "Fondation");
        assertThat(completedStage.getStatus()).isEqualTo(StageStatus.COMPLETED);
        mr.btp.api.category.ExpenseCategory category = categoryRepository.findAll().stream()
                .filter(c -> c.getType() == mr.btp.api.category.CategoryType.MATERIAL)
                .findFirst().orElseThrow();

        InvoiceDtos.InvoiceResponse response = invoiceService.create(new InvoiceDtos.InvoiceRequest(
                InvoiceType.DIRECT_EXPENSE,
                null,
                completedStage.getProject().getId(),
                completedStage.getId(),
                null,
                "DIRECT-COMPLETED-STAGE",
                LocalDate.now(),
                new BigDecimal("75.00"),
                "MRU",
                "Late direct expense",
                "late-direct-expense.jpg",
                InvoiceStatus.CONFIRMED,
                List.of(new InvoiceDtos.InvoiceItemUpsertRequest(
                        null,
                        null,
                        category.getId(),
                        "Late direct expense",
                        null,
                        null,
                        null,
                        new BigDecimal("75.00")
                ))
        ));

        assertThat(response.invoiceType()).isEqualTo(InvoiceType.DIRECT_EXPENSE);
        assertThat(response.stageId()).isEqualTo(completedStage.getId());
        assertThat(response.totalAmount()).isEqualByComparingTo("75.00");
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

    private ConstructionStage activeStageForProject(Long projectId) {
        return stageRepository.findByProjectIdOrderBySortOrderAsc(projectId).stream()
                .filter(stage -> stage.getStatus() != StageStatus.COMPLETED)
                .findFirst()
                .orElseThrow();
    }

    private InvoiceDtos.InvoiceResponse createTestSupply(BigDecimal quantity) {
        InvoiceDtos.InvoiceResponse template = invoiceService.list(0, 50, "SUPPLY").content().stream()
                .filter(candidate -> candidate.projectId() != null)
                .filter(candidate -> !candidate.items().isEmpty())
                .findFirst()
                .orElseThrow();
        InvoiceDtos.InvoiceItemResponse templateItem = template.items().getFirst();
        BigDecimal unitPrice = new BigDecimal("100.00");
        return invoiceService.create(new InvoiceDtos.InvoiceRequest(
                InvoiceType.SUPPLY,
                template.supplierId(),
                template.projectId(),
                null,
                null,
                "TEST-SUPPLY-" + System.nanoTime(),
                LocalDate.now(),
                quantity.multiply(unitPrice),
                template.currency(),
                "Test supply",
                null,
                InvoiceStatus.CONFIRMED,
                java.util.List.of(new InvoiceDtos.InvoiceItemUpsertRequest(
                        null,
                        null,
                        templateItem.categoryId(),
                        templateItem.description(),
                        quantity,
                        templateItem.unit(),
                        unitPrice,
                        quantity.multiply(unitPrice)
                ))
        ));
    }

    private InvoiceDtos.InvoiceResponse createReturn(InvoiceDtos.InvoiceResponse supply, InvoiceDtos.InvoiceItemResponse sourceItem, BigDecimal quantity) {
        return invoiceService.create(new InvoiceDtos.InvoiceRequest(
                InvoiceType.SUPPLY_RETURN,
                null,
                null,
                null,
                supply.id(),
                "RETURN-" + System.nanoTime(),
                LocalDate.now(),
                null,
                null,
                "Supplier return",
                null,
                InvoiceStatus.CONFIRMED,
                java.util.List.of(new InvoiceDtos.InvoiceItemUpsertRequest(null, sourceItem.id(), null, null, quantity, null, null, null))
        ));
    }
}
