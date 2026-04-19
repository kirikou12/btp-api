package mr.btp.api.common.config;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import mr.btp.api.category.CategoryType;
import mr.btp.api.category.ExpenseCategory;
import mr.btp.api.category.ExpenseCategoryRepository;
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
import mr.btp.api.worker.Worker;
import mr.btp.api.worker.WorkerPayment;
import mr.btp.api.worker.WorkerPaymentRepository;
import mr.btp.api.worker.WorkerRepository;
import mr.btp.api.worker.WorkerType;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;

//@Configuration
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
                                   DirectExpenseRepository expenseRepository,
                                   WorkerRepository workerRepository,
                                   WorkerPaymentRepository workerPaymentRepository) {
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
                stageSpec("Foundation", 1, StageStatus.COMPLETED, "25000.00", 100),
                stageSpec("Elevation", 2, StageStatus.IN_PROGRESS, "30000.00", 48),
                stageSpec("Roofing", 3, StageStatus.NOT_STARTED, "18000.00", 0),
                stageSpec("Plumbing", 4, StageStatus.NOT_STARTED, "12000.00", 0),
                stageSpec("Electricity", 5, StageStatus.NOT_STARTED, "10000.00", 0),
                stageSpec("Painting", 6, StageStatus.NOT_STARTED, "9000.00", 0),
                stageSpec("Finishing", 7, StageStatus.NOT_STARTED, "15000.00", 0));

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

            Worker foundationMason = saveWorker(workerRepository, "Mohamed Diallo", WorkerType.MASON, "5000.00");
            Worker electrician = saveWorker(workerRepository, "Sidi Ahmed", WorkerType.ELECTRICIAN, "3000.00");
            saveWorkerPayment(workerPaymentRepository, foundationMason, villaStages.get("Foundation"), "2200.00", today.minusDays(49));
            saveWorkerPayment(workerPaymentRepository, foundationMason, villaStages.get("Elevation"), "1800.00", today.minusDays(18));
            saveWorkerPayment(workerPaymentRepository, electrician, villaStages.get("Elevation"), "650.00", today.minusDays(11));
            saveExpense(expenseRepository, villaHorizon, villaStages.get("Elevation"), categories.get("Transport"), transit,
                "450.00", "Truck transport", today.minusDays(14));
            saveExpense(expenseRepository, villaHorizon, villaStages.get("Elevation"), categories.get("Equipment Rental"), transit,
                "600.00", "Mixer rental", today.minusDays(13));
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
            stages.put(spec.name(), saveStage(repository, project, spec.name(), spec.order(), spec.status(), amount(spec.plannedBudget()), spec.progressPercent()));
        }
        return stages;
    }

    private ConstructionStage saveStage(ConstructionStageRepository repository,
                                        Project project,
                                        String name,
                                        int order,
                                        StageStatus status,
                                        BigDecimal plannedBudget,
                                        int progressPercent) {
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
        stage.setProgressPercent(progressPercent);
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

    private Worker saveWorker(WorkerRepository repository, String name, WorkerType type, String plannedBudget) {
        Worker worker = new Worker();
        worker.setName(name);
        worker.setType(type);
        worker.setPlannedBudget(amount(plannedBudget));
        return repository.save(worker);
    }

    private void saveWorkerPayment(WorkerPaymentRepository repository,
                                   Worker worker,
                                   ConstructionStage stage,
                                   String amount,
                                   LocalDate date) {
        WorkerPayment payment = new WorkerPayment();
        payment.setWorker(worker);
        payment.setStage(stage);
        payment.setAmount(amount(amount));
        payment.setPaymentDate(date);
        repository.save(payment);
    }

    private ExpenseCategory saveCategory(ExpenseCategoryRepository repository, String name, CategoryType type, boolean isSystem) {
        ExpenseCategory category = new ExpenseCategory();
        category.setName(name);
        category.setType(type);
        category.setSystem(isSystem);
        return repository.save(category);
    }

    private StageSeed stageSpec(String name, int order, StageStatus status, String plannedBudget, int progressPercent) {
        return new StageSeed(name, order, status, plannedBudget, progressPercent);
    }

    private BigDecimal amount(String value) {
        return new BigDecimal(value);
    }

    private record StageSeed(String name, int order, StageStatus status, String plannedBudget, int progressPercent) {
    }
}
