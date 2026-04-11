package mr.btp.api.worker;

import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class WorkerController {

    private final WorkerService workerService;

    public WorkerController(WorkerService workerService) {
        this.workerService = workerService;
    }

    @GetMapping("/api/workers")
    public List<WorkerDtos.WorkerResponse> listWorkers() {
        return workerService.listWorkers();
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

    @PostMapping("/api/worker-payments")
    public WorkerDtos.WorkerPaymentResponse createPayment(@Valid @RequestBody WorkerDtos.WorkerPaymentRequest request) {
        return workerService.createPayment(request);
    }

    @GetMapping("/api/worker-payments/{id}")
    public WorkerDtos.WorkerPaymentResponse getPayment(@PathVariable Long id) {
        return workerService.getPayment(id);
    }

    @PutMapping("/api/worker-payments/{id}")
    public WorkerDtos.WorkerPaymentResponse updatePayment(@PathVariable Long id, @Valid @RequestBody WorkerDtos.WorkerPaymentRequest request) {
        return workerService.updatePayment(id, request);
    }

    @DeleteMapping("/api/worker-payments/{id}")
    public void deletePayment(@PathVariable Long id) {
        workerService.deletePayment(id);
    }
}
