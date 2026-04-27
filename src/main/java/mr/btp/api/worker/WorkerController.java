package mr.btp.api.worker;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import java.util.List;
import mr.btp.api.common.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class WorkerController {

    private final WorkerService workerService;
    private final ObjectMapper objectMapper;
    private final Validator validator;

    public WorkerController(WorkerService workerService, ObjectMapper objectMapper, Validator validator) {
        this.workerService = workerService;
        this.objectMapper = objectMapper;
        this.validator = validator;
    }

    @GetMapping("/api/workers")
    public List<WorkerDtos.WorkerResponse> listWorkers(@RequestParam(required = false) Long projectId) {
        return workerService.listWorkers(projectId);
    }

    @PostMapping("/api/workers")
    public WorkerDtos.WorkerResponse createWorker(@Valid @RequestBody WorkerDtos.WorkerRequest request) {
        return workerService.createWorker(request);
    }

    @GetMapping("/api/workers/{id}")
    public WorkerDtos.WorkerResponse getWorker(@PathVariable Long id) {
        return workerService.getWorker(id);
    }

    @PutMapping("/api/workers/{id}")
    public WorkerDtos.WorkerResponse updateWorker(@PathVariable Long id, @Valid @RequestBody WorkerDtos.WorkerRequest request) {
        return workerService.updateWorker(id, request);
    }

    @DeleteMapping("/api/workers/{id}")
    public void deleteWorker(@PathVariable Long id) {
        workerService.deleteWorker(id);
    }

    @GetMapping("/api/projects/{id}/worker-payments")
    public List<WorkerDtos.WorkerPaymentResponse> listPaymentsByProject(@PathVariable Long id) {
        return workerService.paymentsByProject(id);
    }

    @PostMapping(value = "/api/worker-payments", consumes = MediaType.APPLICATION_JSON_VALUE)
    public WorkerDtos.WorkerPaymentResponse createPayment(@Valid @RequestBody WorkerDtos.WorkerPaymentRequest request) {
        return workerService.createPayment(request);
    }

    @PostMapping(value = "/api/worker-payments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public WorkerDtos.WorkerPaymentResponse createPaymentWithDocuments(@RequestPart("payload") String payload,
                                                                       @RequestPart(value = "documents", required = false) List<MultipartFile> documents) {
        return workerService.createPayment(readPayload(payload, WorkerDtos.WorkerPaymentRequest.class), documents);
    }

    @GetMapping("/api/worker-payments/{id}")
    public WorkerDtos.WorkerPaymentResponse getPayment(@PathVariable Long id) {
        return workerService.getPayment(id);
    }

    @PutMapping(value = "/api/worker-payments/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public WorkerDtos.WorkerPaymentResponse updatePayment(@PathVariable Long id, @Valid @RequestBody WorkerDtos.WorkerPaymentRequest request) {
        return workerService.updatePayment(id, request);
    }

    @PutMapping(value = "/api/worker-payments/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public WorkerDtos.WorkerPaymentResponse updatePaymentWithDocuments(@PathVariable Long id,
                                                                       @RequestPart("payload") String payload,
                                                                       @RequestPart(value = "documents", required = false) List<MultipartFile> documents) {
        return workerService.updatePayment(id, readPayload(payload, WorkerDtos.WorkerPaymentRequest.class), documents);
    }

    @DeleteMapping("/api/worker-payments/{id}")
    public void deletePayment(@PathVariable Long id) {
        workerService.deletePayment(id);
    }

    private <T> T readPayload(String payload, Class<T> type) {
        try {
            T request = objectMapper.readValue(payload, type);
            var violations = validator.validate(request);
            if (!violations.isEmpty()) {
                throw new ConstraintViolationException(violations);
            }
            return request;
        } catch (JsonProcessingException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "error.request.invalid-json", "Invalid request payload");
        }
    }
}
