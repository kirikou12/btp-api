package mr.btp.api.worker;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import mr.btp.api.common.exception.ApiException;
import mr.btp.api.common.service.ReferenceDataService;
import mr.btp.api.project.ConstructionStage;
import mr.btp.api.project.StageStatus;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkerService {

    private final WorkerRepository workerRepository;
    private final WorkerPaymentRepository workerPaymentRepository;
    private final WorkerStageBudgetRepository workerStageBudgetRepository;
    private final ReferenceDataService referenceDataService;

    public WorkerService(WorkerRepository workerRepository,
                         WorkerPaymentRepository workerPaymentRepository,
                         WorkerStageBudgetRepository workerStageBudgetRepository,
                         ReferenceDataService referenceDataService) {
        this.workerRepository = workerRepository;
        this.workerPaymentRepository = workerPaymentRepository;
        this.workerStageBudgetRepository = workerStageBudgetRepository;
        this.referenceDataService = referenceDataService;
    }

    @Transactional(readOnly = true)
    public List<WorkerDtos.WorkerResponse> listWorkers() {
        return workerRepository.findAllByOrderByNameAsc().stream().map(this::toWorkerResponse).toList();
    }

    @Transactional(readOnly = true)
    public WorkerDtos.WorkerResponse getWorker(Long id) {
        return toWorkerResponse(referenceDataService.getWorker(id));
    }

    @Transactional
    public WorkerDtos.WorkerResponse createWorker(WorkerDtos.WorkerRequest request) {
        Worker worker = new Worker();
        apply(worker, request);
        Worker saved = workerRepository.save(worker);
        applyStageBudgets(saved, request);
        return toWorkerResponse(saved);
    }

    @Transactional
    public WorkerDtos.WorkerResponse updateWorker(Long id, WorkerDtos.WorkerRequest request) {
        Worker worker = referenceDataService.getWorker(id);
        apply(worker, request);
        Worker saved = workerRepository.save(worker);
        applyStageBudgets(saved, request);
        return toWorkerResponse(saved);
    }

    @Transactional
    public void deleteWorker(Long id) {
        if (!workerRepository.existsById(id)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Worker not found");
        }
        if (!workerPaymentRepository.findByWorker_IdOrderByPaymentDateDesc(id).isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Cannot delete a worker with payments");
        }
        workerRepository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public List<WorkerDtos.WorkerPaymentResponse> paymentsByProject(Long projectId) {
        referenceDataService.getProject(projectId);
        return referenceDataService.workerPaymentsByProject(projectId).stream().map(this::toPaymentResponse).toList();
    }

    @Transactional(readOnly = true)
    public WorkerDtos.WorkerPaymentResponse getPayment(Long id) {
        return toPaymentResponse(referenceDataService.getWorkerPayment(id));
    }

    @Transactional
    public WorkerDtos.WorkerPaymentResponse createPayment(WorkerDtos.WorkerPaymentRequest request) {
        WorkerPayment payment = new WorkerPayment();
        apply(payment, request, false);
        return toPaymentResponse(workerPaymentRepository.save(payment));
    }

    @Transactional
    public WorkerDtos.WorkerPaymentResponse updatePayment(Long id, WorkerDtos.WorkerPaymentRequest request) {
        WorkerPayment payment = referenceDataService.getWorkerPayment(id);
        apply(payment, request, true);
        return toPaymentResponse(workerPaymentRepository.save(payment));
    }

    @Transactional
    public void deletePayment(Long id) {
        if (!workerPaymentRepository.existsById(id)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Worker payment not found");
        }
        workerPaymentRepository.deleteById(id);
    }

    private void apply(Worker worker, WorkerDtos.WorkerRequest request) {
        worker.setName(request.name().trim());
        worker.setType(request.type());
        worker.setProject(request.projectId() == null ? null : referenceDataService.getProject(request.projectId()));
        worker.setPlannedBudget(resolvePlannedBudget(request));
    }

    private BigDecimal resolvePlannedBudget(WorkerDtos.WorkerRequest request) {
        if (request.stageBudgets() == null || request.stageBudgets().isEmpty()) {
            return request.plannedBudget();
        }

        return request.stageBudgets().stream()
                .map(WorkerDtos.WorkerStageBudgetRequest::plannedBudget)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private void applyStageBudgets(Worker worker, WorkerDtos.WorkerRequest request) {
        workerStageBudgetRepository.deleteByWorker_Id(worker.getId());
        if (request.stageBudgets() == null || request.stageBudgets().isEmpty()) {
            return;
        }
        if (worker.getProject() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Stage budgets require a project");
        }

        Set<Long> stageIds = new HashSet<>();
        List<WorkerStageBudget> stageBudgets = request.stageBudgets().stream()
                .filter(item -> item.plannedBudget().compareTo(BigDecimal.ZERO) > 0)
                .map(item -> {
                    if (!stageIds.add(item.stageId())) {
                        throw new ApiException(HttpStatus.BAD_REQUEST, "Each stage can have only one worker budget");
                    }
                    ConstructionStage stage = referenceDataService.getStage(item.stageId());
                    if (!stage.getProject().getId().equals(worker.getProject().getId())) {
                        throw new ApiException(HttpStatus.BAD_REQUEST, "Stage budget must belong to the selected project");
                    }

                    WorkerStageBudget stageBudget = new WorkerStageBudget();
                    stageBudget.setWorker(worker);
                    stageBudget.setStage(stage);
                    stageBudget.setPlannedBudget(item.plannedBudget());
                    return stageBudget;
                })
                .toList();

        workerStageBudgetRepository.saveAll(stageBudgets);
    }

    private void apply(WorkerPayment payment, WorkerDtos.WorkerPaymentRequest request, boolean allowCompletedStage) {
        ConstructionStage stage = referenceDataService.getStage(request.stageId());
        if (!stage.getProject().getId().equals(request.projectId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Stage must belong to the selected project");
        }
        if (stage.getStatus() == StageStatus.COMPLETED && !allowCompletedStage) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Cannot record a worker payment on a completed stage");
        }

        payment.setWorker(referenceDataService.getWorker(request.workerId()));
        payment.setStage(stage);
        payment.setAmount(request.amount());
        payment.setPaymentDate(request.paymentDate());
        payment.setDocumentRef(request.documentRef() == null || request.documentRef().isBlank() ? null : request.documentRef().trim());
    }

    private WorkerDtos.WorkerResponse toWorkerResponse(Worker worker) {
        BigDecimal paid = workerPaymentRepository.findByWorker_IdOrderByPaymentDateDesc(worker.getId()).stream()
                .map(WorkerPayment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        List<WorkerDtos.WorkerStageBudgetResponse> stageBudgets = workerStageBudgetRepository.findByWorker_IdOrderByStage_SortOrderAsc(worker.getId()).stream()
                .map(item -> new WorkerDtos.WorkerStageBudgetResponse(
                        item.getStage().getId(),
                        item.getStage().getName(),
                        item.getPlannedBudget()
                ))
                .toList();
        return new WorkerDtos.WorkerResponse(
                worker.getId(),
                worker.getName(),
                worker.getType(),
                worker.getProject() == null ? null : worker.getProject().getId(),
                worker.getPlannedBudget(),
                stageBudgets,
                paid,
                worker.getPlannedBudget().subtract(paid),
                worker.getCreatedAt(),
                worker.getUpdatedAt()
        );
    }

    private WorkerDtos.WorkerPaymentResponse toPaymentResponse(WorkerPayment payment) {
        ConstructionStage stage = payment.getStage();
        return new WorkerDtos.WorkerPaymentResponse(
                payment.getId(),
                payment.getWorker().getId(),
                payment.getWorker().getName(),
                payment.getWorker().getType(),
                stage.getProject().getId(),
                stage.getId(),
                stage.getName(),
                payment.getAmount(),
                payment.getPaymentDate(),
                payment.getDocumentRef()
        );
    }
}
