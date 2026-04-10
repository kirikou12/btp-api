package mr.btp.api;

import mr.btp.api.document.DocumentStorageService;
import mr.btp.api.consumption.ConsumptionDtos;
import mr.btp.api.consumption.MaterialConsumptionService;
import mr.btp.api.expense.DirectExpenseService;
import mr.btp.api.expense.ExpenseDtos;
import mr.btp.api.invoice.InvoiceDtos;
import mr.btp.api.invoice.InvoiceService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.file.Path;
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
    private DirectExpenseService directExpenseService;

    @Autowired
    private InvoiceService invoiceService;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldPreventOverConsumption() {
        ConsumptionDtos.ConsumptionRequest request = new ConsumptionDtos.ConsumptionRequest(
                1L,
                1L,
                2L,
                2L,
                new BigDecimal("1.00"),
                new BigDecimal("9999.00"),
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
    void shouldAllowUpdatingDirectExpenseOnCompletedStageForCorrections() {
        ExpenseDtos.ExpenseResponse existingExpense = directExpenseService.byProject(1L).stream()
                .filter(expense -> Long.valueOf(1L).equals(expense.stageId()))
                .findFirst()
                .orElseThrow();

        ExpenseDtos.ExpenseResponse updatedExpense = directExpenseService.update(
                existingExpense.id(),
                new ExpenseDtos.ExpenseRequest(
                        existingExpense.projectId(),
                        existingExpense.stageId(),
                        existingExpense.categoryId(),
                        existingExpense.supplierId(),
                        new BigDecimal("2100.00"),
                        "Foundation labor team corrected",
                        existingExpense.subCategory(),
                        existingExpense.documentRef(),
                        existingExpense.expenseDate()
                )
        );

        assertThat(updatedExpense.amount()).isEqualByComparingTo("2100.00");
        assertThat(updatedExpense.description()).contains("corrected");
    }

    @Test
    @Transactional
    void shouldAllowUpdatingConsumptionOnCompletedStageForCorrections() {
        ConsumptionDtos.ConsumptionResponse existingConsumption = materialConsumptionService.byProject(1L).stream()
                .filter(consumption -> Long.valueOf(1L).equals(consumption.stageId()))
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
    void shouldStoreAndLoadUploadedDocument(@TempDir Path tempDirectory) {
        DocumentStorageService documentStorageService = new DocumentStorageService(tempDirectory.toString());
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "proof.jpg",
                "image/jpeg",
                new byte[]{1, 2, 3, 4}
        );

        DocumentStorageService.DocumentUploadResponse uploaded = documentStorageService.storeImage(file);

        assertThat(uploaded.path()).startsWith("/api/uploads/");
        assertThat(documentStorageService.load(uploaded.fileName()).exists()).isTrue();
    }

    @Test
    void shouldExposeActuatorLivenessWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/actuator/health/liveness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }
}
