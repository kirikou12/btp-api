package mr.btp.api.common.config;

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
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

@Configuration
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

            Map<String, ExpenseCategory> categories = new LinkedHashMap<>();
            categories.put("Labor", saveCategory(categoryRepository, "Labor", CategoryType.LABOR, true));
            categories.put("Cement", saveCategory(categoryRepository, "Cement", CategoryType.MATERIAL, true));
            categories.put("Steel", saveCategory(categoryRepository, "Steel", CategoryType.MATERIAL, true));
            categories.put("Water", saveCategory(categoryRepository, "Water", CategoryType.MATERIAL, true));
            categories.put("Paint", saveCategory(categoryRepository, "Paint", CategoryType.MATERIAL, true));
            categories.put("Transport", saveCategory(categoryRepository, "Transport", CategoryType.SERVICE, true));
            categories.put("Equipment Rental", saveCategory(categoryRepository, "Equipment Rental", CategoryType.SERVICE, true));
            categories.put("Misc", saveCategory(categoryRepository, "Misc", CategoryType.MISC, true));

            Project project = new Project();
            project.setName("Villa Horizon");
            project.setLocation("Nouakchott");
            project.setDescription("Demo resale house project");
            project.setStartDate(LocalDate.now().minusMonths(2));
            project.setEstimatedSalePrice(new BigDecimal("185000.00"));
            project.setBudget(new BigDecimal("130000.00"));
            project.setStatus(ProjectStatus.IN_PROGRESS);
            projectRepository.save(project);

            ConstructionStage foundation = saveStage(stageRepository, project, "Foundation", 1, StageStatus.COMPLETED, new BigDecimal("25000.00"));
            ConstructionStage elevation = saveStage(stageRepository, project, "Elevation", 2, StageStatus.IN_PROGRESS, new BigDecimal("30000.00"));
            saveStage(stageRepository, project, "Roofing", 3, StageStatus.NOT_STARTED, new BigDecimal("18000.00"));
            saveStage(stageRepository, project, "Plumbing", 4, StageStatus.NOT_STARTED, new BigDecimal("12000.00"));
            saveStage(stageRepository, project, "Electricity", 5, StageStatus.NOT_STARTED, new BigDecimal("10000.00"));
            saveStage(stageRepository, project, "Painting", 6, StageStatus.NOT_STARTED, new BigDecimal("9000.00"));
            saveStage(stageRepository, project, "Finishing", 7, StageStatus.NOT_STARTED, new BigDecimal("15000.00"));

            Supplier supplier = new Supplier();
            supplier.setName("Atlas Materials");
            supplier.setPhone("+222 45 00 00 00");
            supplier.setEmail("atlas@materials.local");
            supplier.setAddress("Zone Industrielle");
            supplier.setNotes("Primary cement and steel supplier");
            supplierRepository.save(supplier);

            SupplierInvoice invoice = new SupplierInvoice();
            invoice.setSupplier(supplier);
            invoice.setProject(project);
            invoice.setReference("INV-2026-001");
            invoice.setInvoiceDate(LocalDate.now().minusMonths(1));
            invoice.setTotalAmount(new BigDecimal("5000.00"));
            invoice.setCurrency("EUR");
            invoice.setStatus(InvoiceStatus.CONFIRMED);
            invoice.setNotes("Bulk material purchase");
            invoiceRepository.save(invoice);

            SupplierInvoiceItem cement = new SupplierInvoiceItem();
            cement.setInvoice(invoice);
            cement.setCategory(categories.get("Cement"));
            cement.setDescription("Cement stock");
            cement.setQuantity(new BigDecimal("100.00"));
            cement.setUnit("bags");
            cement.setUnitPrice(new BigDecimal("20.00"));
            cement.setTotalAmount(new BigDecimal("2000.00"));
            invoiceItemRepository.save(cement);

            SupplierInvoiceItem steel = new SupplierInvoiceItem();
            steel.setInvoice(invoice);
            steel.setCategory(categories.get("Steel"));
            steel.setDescription("Steel bars");
            steel.setQuantity(new BigDecimal("300.00"));
            steel.setUnit("units");
            steel.setUnitPrice(new BigDecimal("10.00"));
            steel.setTotalAmount(new BigDecimal("3000.00"));
            invoiceItemRepository.save(steel);

            consumptionRepository.save(saveConsumption(project, foundation, cement, categories.get("Cement"), new BigDecimal("25.00"), new BigDecimal("500.00"), LocalDate.now().minusDays(25), "Foundation cement usage"));
            consumptionRepository.save(saveConsumption(project, foundation, steel, categories.get("Steel"), new BigDecimal("100.00"), new BigDecimal("1000.00"), LocalDate.now().minusDays(22), "Foundation steel usage"));
            consumptionRepository.save(saveConsumption(project, elevation, cement, categories.get("Cement"), new BigDecimal("50.00"), new BigDecimal("1000.00"), LocalDate.now().minusDays(7), "Elevation cement usage"));
            consumptionRepository.save(saveConsumption(project, elevation, steel, categories.get("Steel"), new BigDecimal("150.00"), new BigDecimal("1500.00"), LocalDate.now().minusDays(5), "Elevation steel usage"));

            expenseRepository.save(saveExpense(project, foundation, categories.get("Labor"), supplier, new BigDecimal("2200.00"), "Foundation labor team", LocalDate.now().minusDays(26)));
            expenseRepository.save(saveExpense(project, elevation, categories.get("Labor"), supplier, new BigDecimal("1800.00"), "Elevation labor team", LocalDate.now().minusDays(6)));
            expenseRepository.save(saveExpense(project, elevation, categories.get("Transport"), supplier, new BigDecimal("450.00"), "Truck transport", LocalDate.now().minusDays(4)));
        };
    }

    private ExpenseCategory saveCategory(ExpenseCategoryRepository repository, String name, CategoryType type, boolean isSystem) {
        ExpenseCategory category = new ExpenseCategory();
        category.setName(name);
        category.setType(type);
        category.setSystem(isSystem);
        return repository.save(category);
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
        stage.setStartDate(project.getStartDate().plusWeeks(order));
        stage.setPlannedBudget(plannedBudget);
        return repository.save(stage);
    }

    private MaterialConsumption saveConsumption(Project project,
                                                ConstructionStage stage,
                                                SupplierInvoiceItem item,
                                                ExpenseCategory category,
                                                BigDecimal quantityUsed,
                                                BigDecimal amount,
                                                LocalDate date,
                                                String notes) {
        MaterialConsumption consumption = new MaterialConsumption();
        consumption.setProject(project);
        consumption.setStage(stage);
        consumption.setInvoiceItem(item);
        consumption.setCategory(category);
        consumption.setQuantityUsed(quantityUsed);
        consumption.setAmountUsed(amount);
        consumption.setConsumptionDate(date);
        consumption.setNotes(notes);
        return consumption;
    }

    private DirectExpense saveExpense(Project project,
                                      ConstructionStage stage,
                                      ExpenseCategory category,
                                      Supplier supplier,
                                      BigDecimal amount,
                                      String description,
                                      LocalDate date) {
        DirectExpense expense = new DirectExpense();
        expense.setProject(project);
        expense.setStage(stage);
        expense.setCategory(category);
        expense.setSupplier(supplier);
        expense.setAmount(amount);
        expense.setDescription(description);
        expense.setExpenseDate(date);
        return expense;
    }
}
