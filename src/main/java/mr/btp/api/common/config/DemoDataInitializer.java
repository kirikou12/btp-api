package mr.btp.api.common.config;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import mr.btp.api.category.CategoryType;
import mr.btp.api.category.ExpenseCategory;
import mr.btp.api.category.ExpenseCategoryRepository;
import mr.btp.api.consumption.MaterialConsumption;
import mr.btp.api.consumption.MaterialConsumptionRepository;
import mr.btp.api.expense.DirectExpense;
import mr.btp.api.expense.DirectExpenseRepository;
import mr.btp.api.invoice.InvoiceStatus;
import mr.btp.api.invoice.SupplierInvoice;
import mr.btp.api.invoice.SupplierInvoiceItem;
import mr.btp.api.invoice.SupplierInvoiceItemRepository;
import mr.btp.api.invoice.SupplierInvoiceRepository;
import mr.btp.api.project.ConstructionStage;
import mr.btp.api.project.ConstructionStageRepository;
import mr.btp.api.project.Project;
import mr.btp.api.project.ProjectRepository;
import mr.btp.api.project.ProjectStatus;
import mr.btp.api.project.StageStatus;
import mr.btp.api.supplier.Supplier;
import mr.btp.api.supplier.SupplierRepository;
import mr.btp.api.user.User;
import mr.btp.api.user.UserRepository;
import mr.btp.api.user.UserRole;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
@Profile("!prod")
public class DemoDataInitializer {

    @Bean
    CommandLineRunner seedDemoData(UserRepository userRepository,
                                   PasswordEncoder passwordEncoder,
                                   ExpenseCategoryRepository categoryRepository,
                                   ProjectRepository projectRepository,
                                   ConstructionStageRepository stageRepository,
                                   SupplierRepository supplierRepository,
                                   SupplierInvoiceRepository invoiceRepository,
                                   SupplierInvoiceItemRepository invoiceItemRepository,
                                   MaterialConsumptionRepository consumptionRepository,
                                   DirectExpenseRepository expenseRepository) {
        return args -> {
            if (userRepository.count() > 0) {
                return;
            }

            User user = new User();
            user.setEmail("demo@btp.local");
            user.setPasswordHash(passwordEncoder.encode("demo1234"));
            user.setFullName("Demo Manager");
            user.setRole(UserRole.ADMIN);
            userRepository.save(user);

            Map<String, ExpenseCategory> categories = seedCategories(categoryRepository);

            Supplier atlas = saveSupplier(supplierRepository,
                "Atlas Materials",
                "+222 45 00 00 00",
                "atlas@materials.local",
                "Zone Industrielle",
                "Main cement and steel supplier");
            Supplier sahara = saveSupplier(supplierRepository,
                "Sahara Beton",
                "+222 45 11 11 11",
                "contact@saharabeton.local",
                "Ksar route",
                "Concrete blocks and ready-mix");
            Supplier transit = saveSupplier(supplierRepository,
                "Transit Plus",
                "+222 45 22 22 22",
                "ops@transitplus.local",
                "Nouakchott logistics yard",
                "Transport, crane moves, and site deliveries");

            LocalDate today = LocalDate.now();

            Project villaHorizon = saveProject(projectRepository,
                "Villa Horizon",
                "Nouakchott",
                "Resale villa with strong margin target and active structural work.",
                today.minusMonths(4),
                "185000.00",
                "130000.00",
                ProjectStatus.IN_PROGRESS);

            Map<String, ConstructionStage> villaStages = seedStages(stageRepository, villaHorizon,
                stageSpec("Foundation", 1, StageStatus.COMPLETED, "25000.00"),
                stageSpec("Elevation", 2, StageStatus.IN_PROGRESS, "30000.00"),
                stageSpec("Roofing", 3, StageStatus.NOT_STARTED, "18000.00"),
                stageSpec("Plumbing", 4, StageStatus.NOT_STARTED, "12000.00"),
                stageSpec("Electricity", 5, StageStatus.NOT_STARTED, "10000.00"),
                stageSpec("Painting", 6, StageStatus.NOT_STARTED, "9000.00"),
                stageSpec("Finishing", 7, StageStatus.NOT_STARTED, "15000.00"));

            Project palmResidence = saveProject(projectRepository,
                "Palm Residence",
                "Tevragh Zeina",
                "Client home under finishing, used to demonstrate near-delivery profitability.",
                today.minusMonths(7),
                "240000.00",
                "175000.00",
                ProjectStatus.IN_PROGRESS);

            Map<String, ConstructionStage> palmStages = seedStages(stageRepository, palmResidence,
                stageSpec("Foundation", 1, StageStatus.COMPLETED, "32000.00"),
                stageSpec("Elevation", 2, StageStatus.COMPLETED, "36000.00"),
                stageSpec("Roofing", 3, StageStatus.COMPLETED, "22000.00"),
                stageSpec("Plumbing", 4, StageStatus.COMPLETED, "18000.00"),
                stageSpec("Electricity", 5, StageStatus.IN_PROGRESS, "16000.00"),
                stageSpec("Painting", 6, StageStatus.IN_PROGRESS, "14000.00"),
                stageSpec("Finishing", 7, StageStatus.NOT_STARTED, "17000.00"));

            Project depotExtension = saveProject(projectRepository,
                "Depot Extension",
                "Dar Naim",
                "Warehouse extension project used to demonstrate low-margin monitoring.",
                today.minusMonths(2),
                "98000.00",
                "84000.00",
                ProjectStatus.IN_PROGRESS);

            Map<String, ConstructionStage> depotStages = seedStages(stageRepository, depotExtension,
                stageSpec("Foundation", 1, StageStatus.COMPLETED, "16000.00"),
                stageSpec("Steel Structure", 2, StageStatus.IN_PROGRESS, "24000.00"),
                stageSpec("Roofing", 3, StageStatus.NOT_STARTED, "15000.00"),
                stageSpec("Masonry", 4, StageStatus.NOT_STARTED, "12000.00"),
                stageSpec("Finishing", 5, StageStatus.NOT_STARTED, "9000.00"));

            SupplierInvoice villaInvoice1 = saveInvoice(invoiceRepository, atlas, villaHorizon,
                "INV-2026-001", today.minusMonths(2).minusDays(3), "5000.00", "Bulk cement and steel purchase");
            SupplierInvoiceItem villaCement = saveInvoiceItem(invoiceItemRepository, villaInvoice1, categories.get("Cement"),
                "Cement stock", "100.00", "bags", "20.00", "2000.00");
            SupplierInvoiceItem villaSteel = saveInvoiceItem(invoiceItemRepository, villaInvoice1, categories.get("Steel"),
                "Steel bars", "300.00", "units", "10.00", "3000.00");

            SupplierInvoice villaInvoice2 = saveInvoice(invoiceRepository, sahara, villaHorizon,
                "INV-2026-014", today.minusDays(18), "3200.00", "Blocks and sand replenishment");
            SupplierInvoiceItem villaBlocks = saveInvoiceItem(invoiceItemRepository, villaInvoice2, categories.get("Concrete Blocks"),
                "Concrete blocks", "800.00", "units", "2.50", "2000.00");
            SupplierInvoiceItem villaSand = saveInvoiceItem(invoiceItemRepository, villaInvoice2, categories.get("Sand"),
                "River sand", "20.00", "m3", "60.00", "1200.00");

            SupplierInvoice palmInvoice1 = saveInvoice(invoiceRepository, atlas, palmResidence,
                "INV-2026-021", today.minusMonths(3), "8600.00", "Electrical and finishing materials");
            SupplierInvoiceItem palmCables = saveInvoiceItem(invoiceItemRepository, palmInvoice1, categories.get("Electricity"),
                "Electrical cable drums", "12.00", "rolls", "250.00", "3000.00");
            SupplierInvoiceItem palmPaint = saveInvoiceItem(invoiceItemRepository, palmInvoice1, categories.get("Paint"),
                "Facade and interior paint", "80.00", "buckets", "45.00", "3600.00");
            SupplierInvoiceItem palmWaterproof = saveInvoiceItem(invoiceItemRepository, palmInvoice1, categories.get("Waterproofing"),
                "Roof waterproofing membrane", "20.00", "rolls", "100.00", "2000.00");

            SupplierInvoice depotInvoice1 = saveInvoice(invoiceRepository, atlas, depotExtension,
                "INV-2026-030", today.minusDays(32), "6400.00", "Steel structure material package");
            SupplierInvoiceItem depotSteel = saveInvoiceItem(invoiceItemRepository, depotInvoice1, categories.get("Steel"),
                "IPE beams and steel bars", "1.00", "lot", "6400.00", "6400.00");

            SupplierInvoice depotInvoice2 = saveInvoice(invoiceRepository, transit, depotExtension,
                "INV-2026-041", today.minusDays(12), "1800.00", "On-site crane and transport allocation");
            SupplierInvoiceItem depotLogistics = saveInvoiceItem(invoiceItemRepository, depotInvoice2, categories.get("Transport"),
                "Crane and heavy delivery package", "1.00", "service", "1800.00", "1800.00");

            saveConsumption(consumptionRepository, villaHorizon, villaStages.get("Foundation"), villaCement, categories.get("Cement"),
                "25.00", "500.00", today.minusDays(48), "Foundation slab concrete");
            saveConsumption(consumptionRepository, villaHorizon, villaStages.get("Foundation"), villaSteel, categories.get("Steel"),
                "100.00", "1000.00", today.minusDays(45), "Footings and reinforced beams");
            saveConsumption(consumptionRepository, villaHorizon, villaStages.get("Elevation"), villaCement, categories.get("Cement"),
                "50.00", "1000.00", today.minusDays(20), "Block wall mortar");
            saveConsumption(consumptionRepository, villaHorizon, villaStages.get("Elevation"), villaSteel, categories.get("Steel"),
                "150.00", "1500.00", today.minusDays(15), "Columns and lintels");
            saveConsumption(consumptionRepository, villaHorizon, villaStages.get("Elevation"), villaBlocks, categories.get("Concrete Blocks"),
                "260.00", "650.00", today.minusDays(12), "Elevation wall sections");
            saveConsumption(consumptionRepository, villaHorizon, villaStages.get("Elevation"), villaSand, categories.get("Sand"),
                "8.00", "480.00", today.minusDays(10), "Mortar and render mix");

            saveConsumption(consumptionRepository, palmResidence, palmStages.get("Electricity"), palmCables, categories.get("Electricity"),
                "7.00", "1750.00", today.minusDays(11), "Main circuits and panel wiring");
            saveConsumption(consumptionRepository, palmResidence, palmStages.get("Painting"), palmPaint, categories.get("Paint"),
                "32.00", "1440.00", today.minusDays(7), "Interior first coat");
            saveConsumption(consumptionRepository, palmResidence, palmStages.get("Roofing"), palmWaterproof, categories.get("Waterproofing"),
                "12.00", "1200.00", today.minusDays(70), "Roof terrace membrane");

            saveConsumption(consumptionRepository, depotExtension, depotStages.get("Steel Structure"), depotSteel, categories.get("Steel"),
                "0.55", "3520.00", today.minusDays(9), "Main frame assembly");
            saveConsumption(consumptionRepository, depotExtension, depotStages.get("Steel Structure"), depotLogistics, categories.get("Transport"),
                "0.50", "900.00", today.minusDays(8), "Crane package used for steel erection");

            saveExpense(expenseRepository, villaHorizon, villaStages.get("Foundation"), categories.get("Labor"), null,
                "2200.00", "Foundation labor team", today.minusDays(49));
            saveExpense(expenseRepository, villaHorizon, villaStages.get("Elevation"), categories.get("Labor"), null,
                "1800.00", "Elevation labor team", today.minusDays(18));
            saveExpense(expenseRepository, villaHorizon, villaStages.get("Elevation"), categories.get("Transport"), transit,
                "450.00", "Truck transport", today.minusDays(14));
            saveExpense(expenseRepository, villaHorizon, villaStages.get("Elevation"), categories.get("Equipment Rental"), transit,
                "600.00", "Mixer rental", today.minusDays(13));

            saveExpense(expenseRepository, palmResidence, palmStages.get("Electricity"), categories.get("Labor"), null,
                "2400.00", "Electrical subcontractor advance", today.minusDays(10));
            saveExpense(expenseRepository, palmResidence, palmStages.get("Painting"), categories.get("Labor"), null,
                "1600.00", "Painting crew week 1", today.minusDays(6));
            saveExpense(expenseRepository, palmResidence, palmStages.get("Painting"), categories.get("Misc"), null,
                "320.00", "Cleaning supplies and masking", today.minusDays(5));

            saveExpense(expenseRepository, depotExtension, depotStages.get("Foundation"), categories.get("Labor"), null,
                "1450.00", "Concrete crew", today.minusDays(27));
            saveExpense(expenseRepository, depotExtension, depotStages.get("Steel Structure"), categories.get("Labor"), null,
                "2100.00", "Steel installers", today.minusDays(8));
            saveExpense(expenseRepository, depotExtension, depotStages.get("Steel Structure"), categories.get("Equipment Rental"), transit,
                "950.00", "Welding generator rental", today.minusDays(7));
        };
    }

    private Map<String, ExpenseCategory> seedCategories(ExpenseCategoryRepository categoryRepository) {
        Map<String, ExpenseCategory> categories = new LinkedHashMap<>();
        categories.put("Labor", saveCategory(categoryRepository, "Labor", CategoryType.LABOR, true));
        categories.put("Cement", saveCategory(categoryRepository, "Cement", CategoryType.MATERIAL, true));
        categories.put("Steel", saveCategory(categoryRepository, "Steel", CategoryType.MATERIAL, true));
        categories.put("Sand", saveCategory(categoryRepository, "Sand", CategoryType.MATERIAL, true));
        categories.put("Concrete Blocks", saveCategory(categoryRepository, "Concrete Blocks", CategoryType.MATERIAL, true));
        categories.put("Paint", saveCategory(categoryRepository, "Paint", CategoryType.MATERIAL, true));
        categories.put("Electricity", saveCategory(categoryRepository, "Electricity", CategoryType.MATERIAL, true));
        categories.put("Waterproofing", saveCategory(categoryRepository, "Waterproofing", CategoryType.MATERIAL, true));
        categories.put("Transport", saveCategory(categoryRepository, "Transport", CategoryType.SERVICE, true));
        categories.put("Equipment Rental", saveCategory(categoryRepository, "Equipment Rental", CategoryType.SERVICE, true));
        categories.put("Misc", saveCategory(categoryRepository, "Misc", CategoryType.MISC, true));
        return categories;
    }

    private Project saveProject(ProjectRepository repository,
                                String name,
                                String location,
                                String description,
                                LocalDate startDate,
                                String estimatedSalePrice,
                                String budget,
                                ProjectStatus status) {
        Project project = new Project();
        project.setName(name);
        project.setLocation(location);
        project.setDescription(description);
        project.setStartDate(startDate);
        project.setEstimatedSalePrice(amount(estimatedSalePrice));
        project.setBudget(amount(budget));
        project.setStatus(status);
        return repository.save(project);
    }

    private Map<String, ConstructionStage> seedStages(ConstructionStageRepository repository,
                                                      Project project,
                                                      StageSeed... specs) {
        Map<String, ConstructionStage> stages = new LinkedHashMap<>();
        for (StageSeed spec : specs) {
            stages.put(spec.name(), saveStage(repository, project, spec.name(), spec.order(), spec.status(), amount(spec.plannedBudget())));
        }
        return stages;
    }

    private ConstructionStage saveStage(ConstructionStageRepository repository,
                                        Project project,
                                        String name,
                                        int order,
                                        StageStatus status,
                                        BigDecimal plannedBudget) {
        ConstructionStage stage = new ConstructionStage();
        stage.setProject(project);
        stage.setName(name);
        stage.setSortOrder(order);
        stage.setStatus(status);
        stage.setStartDate(project.getStartDate().plusWeeks(order * 2L));
        if (status == StageStatus.COMPLETED) {
            stage.setEndDate(project.getStartDate().plusWeeks(order * 2L + 1));
        }
        stage.setPlannedBudget(plannedBudget);
        return repository.save(stage);
    }

    private Supplier saveSupplier(SupplierRepository repository,
                                  String name,
                                  String phone,
                                  String email,
                                  String address,
                                  String notes) {
        Supplier supplier = new Supplier();
        supplier.setName(name);
        supplier.setPhone(phone);
        supplier.setEmail(email);
        supplier.setAddress(address);
        supplier.setNotes(notes);
        return repository.save(supplier);
    }

    private SupplierInvoice saveInvoice(SupplierInvoiceRepository repository,
                                        Supplier supplier,
                                        Project project,
                                        String reference,
                                        LocalDate invoiceDate,
                                        String totalAmount,
                                        String notes) {
        SupplierInvoice invoice = new SupplierInvoice();
        invoice.setSupplier(supplier);
        invoice.setProject(project);
        invoice.setReference(reference);
        invoice.setInvoiceDate(invoiceDate);
        invoice.setTotalAmount(amount(totalAmount));
        invoice.setCurrency("EUR");
        invoice.setStatus(InvoiceStatus.CONFIRMED);
        invoice.setNotes(notes);
        return repository.save(invoice);
    }

    private SupplierInvoiceItem saveInvoiceItem(SupplierInvoiceItemRepository repository,
                                                SupplierInvoice invoice,
                                                ExpenseCategory category,
                                                String description,
                                                String quantity,
                                                String unit,
                                                String unitPrice,
                                                String totalAmount) {
        SupplierInvoiceItem item = new SupplierInvoiceItem();
        item.setInvoice(invoice);
        item.setCategory(category);
        item.setDescription(description);
        item.setQuantity(amount(quantity));
        item.setUnit(unit);
        item.setUnitPrice(amount(unitPrice));
        item.setTotalAmount(amount(totalAmount));
        return repository.save(item);
    }

    private void saveConsumption(MaterialConsumptionRepository repository,
                                 Project project,
                                 ConstructionStage stage,
                                 SupplierInvoiceItem item,
                                 ExpenseCategory category,
                                 String quantityUsed,
                                 String amountUsed,
                                 LocalDate date,
                                 String notes) {
        MaterialConsumption consumption = new MaterialConsumption();
        consumption.setProject(project);
        consumption.setStage(stage);
        consumption.setInvoiceItem(item);
        consumption.setCategory(category);
        consumption.setQuantityUsed(amount(quantityUsed));
        consumption.setAmountUsed(amount(amountUsed));
        consumption.setConsumptionDate(date);
        consumption.setNotes(notes);
        repository.save(consumption);
    }

    private void saveExpense(DirectExpenseRepository repository,
                             Project project,
                             ConstructionStage stage,
                             ExpenseCategory category,
                             Supplier supplier,
                             String amount,
                             String description,
                             LocalDate date) {
        DirectExpense expense = new DirectExpense();
        expense.setProject(project);
        expense.setStage(stage);
        expense.setCategory(category);
        expense.setSupplier(supplier);
        expense.setAmount(amount(amount));
        expense.setDescription(description);
        expense.setExpenseDate(date);
        repository.save(expense);
    }

    private ExpenseCategory saveCategory(ExpenseCategoryRepository repository, String name, CategoryType type, boolean isSystem) {
        ExpenseCategory category = new ExpenseCategory();
        category.setName(name);
        category.setType(type);
        category.setSystem(isSystem);
        return repository.save(category);
    }

    private StageSeed stageSpec(String name, int order, StageStatus status, String plannedBudget) {
        return new StageSeed(name, order, status, plannedBudget);
    }

    private BigDecimal amount(String value) {
        return new BigDecimal(value);
    }

    private record StageSeed(String name, int order, StageStatus status, String plannedBudget) {
    }
}
