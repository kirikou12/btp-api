package mr.btp.api;

import mr.btp.api.category.CategoryDtos;
import mr.btp.api.category.CategoryService;
import mr.btp.api.document.CloudinaryStorageClient;
import mr.btp.api.document.DocumentUploadClient;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.when;
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
    private CategoryService categoryService;

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

    @MockBean
    private DocumentUploadClient documentUploadClient;

    @Autowired
    private MockMvc mockMvc;

    @BeforeEach
    void mockCloudinaryUploads() {
        when(documentUploadClient.uploadImage(any(byte[].class), nullable(String.class), nullable(String.class)))
                .thenAnswer(invocation -> {
                    byte[] content = invocation.getArgument(0);
                    String publicId = "btp/documents/" + UUID.randomUUID();
                    return new CloudinaryStorageClient.CloudinaryUpload(
                            "https://res.cloudinary.com/demo/image/upload/v1/" + publicId + ".jpg",
                            publicId,
                            "image",
                            "jpg",
                            content.length
                    );
                });
    }

    @Test
    @WithMockUser
    @Transactional
    void shouldNotDeleteCategoryInUseByExpense() throws Exception {
        List<CategoryDtos.CategoryResponse> categories = categoryService.list();
        CategoryDtos.CategoryResponse inUseCategory = categories.stream()
                .filter(c -> !c.isSystem())
                .findFirst()
                .orElseThrow();

        mockMvc.perform(delete("/api/categories/" + inUseCategory.id()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("error.category.in-use"));
    }

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
                        .map(item -> new InvoiceDtos.InvoiceItemUpsertRequest(null, item.id(), null, null, BigDecimal.ONE, null, null, null, null))
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
                        new BigDecimal("125.00"),
                        null
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
    void shouldCreateSupplyExchangeWithReplacementMaterialLines() {
        InvoiceDtos.InvoiceResponse supply = createTestSupply(new BigDecimal("10.00"));
        Long replacementCategoryId = anotherMaterialCategoryId(supply.items().getFirst().categoryId());

        InvoiceDtos.InvoiceResponse exchange = createExchange(supply, replacementCategoryId, new BigDecimal("250.00"));

        assertThat(exchange.invoiceType()).isEqualTo(InvoiceType.SUPPLY_EXCHANGE);
        assertThat(exchange.sourceSupplyInvoiceId()).isEqualTo(supply.id());
        assertThat(exchange.supplierId()).isEqualTo(supply.supplierId());
        assertThat(exchange.projectId()).isNull();
        assertThat(exchange.stageId()).isNull();
        assertThat(exchange.totalAmount()).isEqualByComparingTo("250.00");
        assertThat(exchange.items()).hasSize(1);
        assertThat(exchange.items().getFirst().sourceSupplyItemId()).isNull();
        assertThat(exchange.items().getFirst().categoryId()).isEqualTo(replacementCategoryId);
        assertThat(invoiceService.list(0, 50, "SUPPLY_EXCHANGE").content())
                .extracting(InvoiceDtos.InvoiceResponse::id)
                .contains(exchange.id());
    }

    @Test
    @Transactional
    void shouldRejectSupplyExchangeAboveAvailableSourceBalance() {
        InvoiceDtos.InvoiceResponse supply = createTestSupply(new BigDecimal("10.00"));
        Long replacementCategoryId = anotherMaterialCategoryId(supply.items().getFirst().categoryId());

        createExchange(supply, replacementCategoryId, new BigDecimal("250.00"));

        assertThatThrownBy(() -> createExchange(supply, replacementCategoryId, new BigDecimal("750.01")))
                .hasMessageContaining("Exchange amount exceeds available source supply invoice balance");
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
                java.util.List.of(new InvoiceDtos.InvoiceItemUpsertRequest(null, sourceItem.id(), null, null, new BigDecimal("3.00"), null, null, null, null))
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
                java.util.List.of(new InvoiceDtos.InvoiceItemUpsertRequest(null, sourceItem.id(), null, null, new BigDecimal("11.00"), null, null, null, null))
        ))).hasMessageContaining("Return quantity exceeds available supply quantity");
    }

    @Test
    @Transactional
    void shouldUpdateSupplyInvoiceWithOutgoingItemsAndPropagateChanges() {
        InvoiceDtos.InvoiceResponse supply = createTestSupply(new BigDecimal("10.00"));
        InvoiceDtos.InvoiceItemResponse sourceItem = supply.items().getFirst();
        ConstructionStage activeStage = activeStageForProject(supply.projectId());

        InvoiceDtos.InvoiceResponse usage = invoiceService.createUsage(new InvoiceDtos.UsageInvoiceRequest(
                supply.id(),
                supply.projectId(),
                activeStage.getId(),
                LocalDate.now(),
                "Usage before supply edit",
                null,
                InvoiceStatus.CONFIRMED,
                List.of(new InvoiceDtos.UsageInvoiceItemRequest(sourceItem.id(), new BigDecimal("4.00")))
        ));
        InvoiceDtos.InvoiceResponse supplyReturn = createReturn(supply, sourceItem, BigDecimal.ONE);
        Long updatedCategoryId = anotherMaterialCategoryId(sourceItem.categoryId());

        InvoiceDtos.InvoiceResponse updatedSupply = invoiceService.update(supply.id(), new InvoiceDtos.InvoiceRequest(
                InvoiceType.SUPPLY,
                supply.supplierId(),
                supply.projectId(),
                null,
                null,
                "SUPPLY-UPDATED",
                LocalDate.now(),
                new BigDecimal("1160.00"),
                supply.currency(),
                "Updated supply after outgoing entries",
                null,
                InvoiceStatus.CONFIRMED,
                List.of(
                        new InvoiceDtos.InvoiceItemUpsertRequest(
                                null,
                                null,
                                updatedCategoryId,
                                "Updated source item",
                                new BigDecimal("8.00"),
                                sourceItem.unit(),
                                new BigDecimal("120.00"),
                                new BigDecimal("960.00"),
                                sourceItem.id()
                        ),
                        new InvoiceDtos.InvoiceItemUpsertRequest(
                                null,
                                null,
                                sourceItem.categoryId(),
                                "Additional material",
                                new BigDecimal("2.00"),
                                sourceItem.unit(),
                                new BigDecimal("100.00"),
                                new BigDecimal("200.00"),
                                null
                        )
                )
        ));

        assertThat(updatedSupply.items()).hasSize(2);
        InvoiceDtos.InvoiceResponse refreshedSupply = invoiceService.get(supply.id());
        InvoiceDtos.InvoiceItemResponse refreshedSourceItem = refreshedSupply.items().stream()
                .filter(item -> item.id().equals(sourceItem.id()))
                .findFirst()
                .orElseThrow();
        assertThat(refreshedSourceItem.categoryId()).isEqualTo(updatedCategoryId);
        assertThat(refreshedSourceItem.description()).isEqualTo("Updated source item");
        assertThat(refreshedSourceItem.quantity()).isEqualByComparingTo("8.00");
        assertThat(refreshedSourceItem.unitPrice()).isEqualByComparingTo("120.00");
        assertThat(refreshedSourceItem.availableQuantity()).isEqualByComparingTo("3.00");

        InvoiceDtos.InvoiceResponse refreshedUsage = invoiceService.get(usage.id());
        assertThat(refreshedUsage.totalAmount()).isEqualByComparingTo("480.00");
        assertThat(refreshedUsage.items()).singleElement().satisfies(item -> {
            assertThat(item.categoryId()).isEqualTo(updatedCategoryId);
            assertThat(item.description()).isEqualTo("Updated source item");
            assertThat(item.unitPrice()).isEqualByComparingTo("120.00");
            assertThat(item.totalAmount()).isEqualByComparingTo("480.00");
        });

        InvoiceDtos.InvoiceResponse refreshedReturn = invoiceService.get(supplyReturn.id());
        assertThat(refreshedReturn.totalAmount()).isEqualByComparingTo("120.00");
        assertThat(refreshedReturn.items()).singleElement().satisfies(item -> {
            assertThat(item.categoryId()).isEqualTo(updatedCategoryId);
            assertThat(item.description()).isEqualTo("Updated source item");
            assertThat(item.unitPrice()).isEqualByComparingTo("120.00");
            assertThat(item.totalAmount()).isEqualByComparingTo("120.00");
        });
    }

    @Test
    @Transactional
    void shouldRejectSupplyQuantityDecreaseBelowOutgoingQuantity() {
        InvoiceDtos.InvoiceResponse supply = createTestSupply(new BigDecimal("10.00"));
        InvoiceDtos.InvoiceItemResponse sourceItem = supply.items().getFirst();
        ConstructionStage activeStage = activeStageForProject(supply.projectId());

        invoiceService.createUsage(new InvoiceDtos.UsageInvoiceRequest(
                supply.id(),
                supply.projectId(),
                activeStage.getId(),
                LocalDate.now(),
                "Usage before invalid supply edit",
                null,
                InvoiceStatus.CONFIRMED,
                List.of(new InvoiceDtos.UsageInvoiceItemRequest(sourceItem.id(), new BigDecimal("4.00")))
        ));
        createReturn(supply, sourceItem, new BigDecimal("2.00"));

        assertThatThrownBy(() -> invoiceService.update(supply.id(), new InvoiceDtos.InvoiceRequest(
                InvoiceType.SUPPLY,
                supply.supplierId(),
                supply.projectId(),
                null,
                null,
                "SUPPLY-TOO-SMALL",
                LocalDate.now(),
                new BigDecimal("500.00"),
                supply.currency(),
                "Too small after outgoing entries",
                null,
                InvoiceStatus.CONFIRMED,
                List.of(new InvoiceDtos.InvoiceItemUpsertRequest(
                        null,
                        null,
                        sourceItem.categoryId(),
                        sourceItem.description(),
                        new BigDecimal("5.00"),
                        sourceItem.unit(),
                        new BigDecimal("100.00"),
                        new BigDecimal("500.00"),
                        sourceItem.id()
                ))
        ))).hasMessageContaining("Item '" + sourceItem.description() + "' quantity cannot be reduced below outgoing quantity")
                .hasMessageContaining("Outgoing quantity: 6.00");
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

        WorkerDtos.WorkerPaymentResponse payment = workerService.createPayment(
                new WorkerDtos.WorkerPaymentRequest(
                        worker.id(),
                        completedStage.getProject().getId(),
                        completedStage.getId(),
                        new BigDecimal("125.00"),
                        LocalDate.now(),
                        null
                ),
                List.of(
                        new MockMultipartFile("documents", "late-receipt-front.jpg", "image/jpeg", new byte[]{1, 2}),
                        new MockMultipartFile("documents", "late-receipt-back.jpg", "image/jpeg", new byte[]{3, 4})
                )
        );

        assertThat(payment.stageId()).isEqualTo(completedStage.getId());
        assertThat(payment.amount()).isEqualByComparingTo("125.00");
        assertThat(payment.documents()).hasSize(2);
        assertThat(payment.documents()).extracting(mr.btp.api.document.DocumentDtos.DocumentAttachmentResponse::originalFileName)
                .containsExactly("late-receipt-front.jpg", "late-receipt-back.jpg");
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
                        existingPayment.documents().stream()
                                .map(mr.btp.api.document.DocumentDtos.DocumentAttachmentResponse::id)
                                .toList()
                )
        );

        assertThat(updatedPayment.amount()).isEqualByComparingTo("2100.00");
        assertThat(updatedPayment.workerName()).isEqualTo(existingPayment.workerName());
    }

    @Test
    @Transactional
    void shouldUploadDocumentToCloudinary() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "proof.jpg",
                "image/jpeg",
                new byte[]{1, 2, 3, 4}
        );

        DocumentStorageService.DocumentUploadResponse uploaded = documentStorageService.storeImage(file);

        assertThat(uploaded.path()).startsWith("https://res.cloudinary.com/");
        assertThat(uploaded.publicId()).startsWith("btp/documents/");
        assertThat(uploaded.contentType()).isEqualTo("image/jpeg");
        assertThat(uploaded.size()).isEqualTo(4);
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

        InvoiceDtos.InvoiceResponse response = invoiceService.create(
                new InvoiceDtos.InvoiceRequest(
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
                        null,
                        InvoiceStatus.CONFIRMED,
                        List.of(new InvoiceDtos.InvoiceItemUpsertRequest(
                                null,
                                null,
                                category.getId(),
                                "Direct material expense",
                                null,
                                null,
                                null,
                                new BigDecimal("100.00"),
                                null
                        ))
                ),
                List.of(
                        new MockMultipartFile("documents", "proof-front.jpg", "image/jpeg", new byte[]{1, 2}),
                        new MockMultipartFile("documents", "proof-back.jpg", "image/jpeg", new byte[]{3, 4})
                )
        );

        assertThat(response.invoiceType()).isEqualTo(InvoiceType.DIRECT_EXPENSE);
        assertThat(response.supplierId()).isNull();
        assertThat(response.invoiceDate()).isEqualTo(LocalDate.now());
        assertThat(response.documents()).hasSize(2);
        assertThat(response.documents()).extracting(mr.btp.api.document.DocumentDtos.DocumentAttachmentResponse::originalFileName)
                .containsExactly("proof-front.jpg", "proof-back.jpg");
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
                null,
                InvoiceStatus.CONFIRMED,
                List.of(new InvoiceDtos.InvoiceItemUpsertRequest(
                        null,
                        null,
                        category.getId(),
                        "Late direct expense",
                        null,
                        null,
                        null,
                        new BigDecimal("75.00"),
                        null
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
                        quantity.multiply(unitPrice),
                        null
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
                java.util.List.of(new InvoiceDtos.InvoiceItemUpsertRequest(null, sourceItem.id(), null, null, quantity, null, null, null, null))
        ));
    }

    private InvoiceDtos.InvoiceResponse createExchange(InvoiceDtos.InvoiceResponse supply, Long replacementCategoryId, BigDecimal totalAmount) {
        return invoiceService.create(new InvoiceDtos.InvoiceRequest(
                InvoiceType.SUPPLY_EXCHANGE,
                supply.supplierId(),
                null,
                null,
                supply.id(),
                "EXCHANGE-" + System.nanoTime(),
                LocalDate.now(),
                totalAmount,
                supply.currency(),
                "Supplier exchange",
                null,
                InvoiceStatus.CONFIRMED,
                java.util.List.of(new InvoiceDtos.InvoiceItemUpsertRequest(
                        null,
                        null,
                        replacementCategoryId,
                        "Replacement material",
                        BigDecimal.ONE,
                        "unit",
                        totalAmount,
                        totalAmount,
                        null
                ))
        ));
    }

    private Long anotherMaterialCategoryId(Long excludingCategoryId) {
        return categoryRepository.findAll().stream()
                .filter(category -> category.getType() == mr.btp.api.category.CategoryType.MATERIAL)
                .map(mr.btp.api.category.ExpenseCategory::getId)
                .filter(categoryId -> !categoryId.equals(excludingCategoryId))
                .findFirst()
                .orElseThrow();
    }
}
