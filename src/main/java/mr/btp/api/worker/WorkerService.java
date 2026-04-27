package mr.btp.api.worker;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import mr.btp.api.common.exception.ApiException;
import mr.btp.api.common.i18n.MessageKey;
import mr.btp.api.common.service.ReferenceDataService;
import mr.btp.api.document.DocumentDtos;
import mr.btp.api.document.DocumentStorageService;
import mr.btp.api.project.ConstructionStage;
import mr.btp.api.project.Project;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

@Service
public class WorkerService {

    private final WorkerRepository workerRepository;
    private final WorkerPaymentRepository workerPaymentRepository;
    private final WorkerStageBudgetRepository workerStageBudgetRepository;
    private final ReferenceDataService referenceDataService;
    private final DocumentStorageService documentStorageService;

    public WorkerService(WorkerRepository workerRepository,
                         WorkerPaymentRepository workerPaymentRepository,
                         WorkerStageBudgetRepository workerStageBudgetRepository,
                         ReferenceDataService referenceDataService,
                         DocumentStorageService documentStorageService) {
        this.workerRepository = workerRepository;
        this.workerPaymentRepository = workerPaymentRepository;
        this.workerStageBudgetRepository = workerStageBudgetRepository;
        this.referenceDataService = referenceDataService;
        this.documentStorageService = documentStorageService;
    }

    @Transactional(readOnly = true)
    public List<WorkerDtos.WorkerResponse> listWorkers() {
        return listWorkers(null);
    }

    @Transactional(readOnly = true)
    public List<WorkerDtos.WorkerResponse> listWorkers(Long projectId) {
        List<Worker> workers;
        if (projectId == null) {
            workers = workerRepository.findAllByOrderByNameAsc();
        } else {
            referenceDataService.getProject(projectId);
            workers = workerRepository.findByProjectMembershipOrderByNameAsc(projectId);
        }
        return toWorkerResponses(workers);
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
        if (applyStageBudgets(saved, request)) {
            recalculatePlannedBudget(saved);
        }
        return toWorkerResponse(saved);
    }

    @Transactional
    public WorkerDtos.WorkerResponse updateWorker(Long id, WorkerDtos.WorkerRequest request) {
        Worker worker = referenceDataService.getWorker(id);
        apply(worker, request);
        Worker saved = workerRepository.save(worker);
        if (applyStageBudgets(saved, request)) {
            recalculatePlannedBudget(saved);
        }
        return toWorkerResponse(saved);
    }

    @Transactional
    public void deleteWorker(Long id) {
        if (!workerRepository.existsById(id)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "error.resource.not-found", "{0} not found", MessageKey.of("resource.worker", "Worker"));
        }
        if (!workerPaymentRepository.findByWorker_IdOrderByPaymentDateDesc(id).isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "error.worker.delete.has-payments", "Cannot delete a worker with payments");
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
        return createPayment(request, List.of());
    }

    @Transactional
    public WorkerDtos.WorkerPaymentResponse createPayment(WorkerDtos.WorkerPaymentRequest request, List<MultipartFile> documentFiles) {
        WorkerPayment payment = new WorkerPayment();
        apply(payment, request);
        WorkerPayment saved = workerPaymentRepository.save(payment);
        syncImages(saved, request.documentIds(), documentFiles);
        return toPaymentResponse(workerPaymentRepository.save(saved));
    }

    @Transactional
    public WorkerDtos.WorkerPaymentResponse updatePayment(Long id, WorkerDtos.WorkerPaymentRequest request) {
        return updatePayment(id, request, List.of());
    }

    @Transactional
    public WorkerDtos.WorkerPaymentResponse updatePayment(Long id, WorkerDtos.WorkerPaymentRequest request, List<MultipartFile> documentFiles) {
        WorkerPayment payment = referenceDataService.getWorkerPayment(id);
        apply(payment, request);
        syncImages(payment, request.documentIds(), documentFiles);
        return toPaymentResponse(workerPaymentRepository.save(payment));
    }

    @Transactional
    public void deletePayment(Long id) {
        if (!workerPaymentRepository.existsById(id)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "error.resource.not-found", "{0} not found", MessageKey.of("resource.worker-payment", "Worker payment"));
        }
        WorkerPayment payment = referenceDataService.getWorkerPayment(id);
        List<String> publicIds = imagePublicIds(payment);
        workerPaymentRepository.delete(payment);
        registerRemovedDocumentCommitCleanup(publicIds);
    }

    private void apply(Worker worker, WorkerDtos.WorkerRequest request) {
        worker.setName(request.name().trim());
        worker.setType(request.type());
        worker.setProjects(resolveProjects(request));
        worker.setPlannedBudget(resolvePlannedBudget(request));
    }

    private Set<Project> resolveProjects(WorkerDtos.WorkerRequest request) {
        Set<Long> projectIds = new LinkedHashSet<>();
        if (request.projectIds() != null) {
            request.projectIds().stream()
                    .filter(id -> id != null && id > 0)
                    .forEach(projectIds::add);
        } else if (request.projectId() != null) {
            projectIds.add(request.projectId());
        }

        return projectIds.stream()
                .map(referenceDataService::getProject)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    private BigDecimal resolvePlannedBudget(WorkerDtos.WorkerRequest request) {
        if (request.stageBudgets() == null || request.stageBudgets().isEmpty()) {
            return request.plannedBudget();
        }

        return request.stageBudgets().stream()
                .map(WorkerDtos.WorkerStageBudgetRequest::plannedBudget)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private boolean applyStageBudgets(Worker worker, WorkerDtos.WorkerRequest request) {
        if (request.stageBudgets() == null) {
            return false;
        }

        Long budgetProjectId = resolveBudgetProjectId(request);
        if (budgetProjectId == null) {
            workerStageBudgetRepository.deleteByWorker_Id(worker.getId());
        } else {
            workerStageBudgetRepository.deleteByWorker_IdAndStage_Project_Id(worker.getId(), budgetProjectId);
        }
        workerStageBudgetRepository.flush();
        if (request.stageBudgets().isEmpty()) {
            return true;
        }

        Set<Long> stageIds = new HashSet<>();
        List<WorkerStageBudget> stageBudgets = request.stageBudgets().stream()
                .filter(item -> item.plannedBudget().compareTo(BigDecimal.ZERO) > 0)
                .map(item -> {
                    if (!stageIds.add(item.stageId())) {
                        throw new ApiException(HttpStatus.BAD_REQUEST, "error.worker-budget.stage-unique", "Each stage can have only one worker budget");
                    }
                    ConstructionStage stage = referenceDataService.getStage(item.stageId());
                    Long stageProjectId = stage.getProject().getId();
                    if (!stageProjectId.equals(budgetProjectId)) {
                        throw new ApiException(HttpStatus.BAD_REQUEST, "error.worker-budget.stage-project-mismatch", "Stage budget must belong to the selected project");
                    }
                    if (worker.getProjects().stream().noneMatch(project -> project.getId().equals(stageProjectId))) {
                        throw new ApiException(HttpStatus.BAD_REQUEST, "error.worker-budget.worker-project-required", "Stage budgets require the worker to be attached to the selected project");
                    }

                    WorkerStageBudget stageBudget = new WorkerStageBudget();
                    stageBudget.setWorker(worker);
                    stageBudget.setStage(stage);
                    stageBudget.setPlannedBudget(item.plannedBudget());
                    return stageBudget;
                })
                .toList();

        workerStageBudgetRepository.saveAll(stageBudgets);
        return true;
    }

    private Long resolveBudgetProjectId(WorkerDtos.WorkerRequest request) {
        if (request.projectId() != null) {
            return request.projectId();
        }
        if (request.stageBudgets() == null || request.stageBudgets().isEmpty()) {
            return null;
        }
        ConstructionStage firstStage = referenceDataService.getStage(request.stageBudgets().getFirst().stageId());
        return firstStage.getProject().getId();
    }

    private void recalculatePlannedBudget(Worker worker) {
        List<WorkerStageBudget> stageBudgets = workerStageBudgetRepository.findByWorker_IdOrderByStage_SortOrderAsc(worker.getId());
        if (stageBudgets.isEmpty()) {
            return;
        }

        worker.setPlannedBudget(stageBudgets.stream()
                .map(WorkerStageBudget::getPlannedBudget)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        workerRepository.save(worker);
    }

    private void apply(WorkerPayment payment, WorkerDtos.WorkerPaymentRequest request) {
        ConstructionStage stage = referenceDataService.getStage(request.stageId());
        if (!stage.getProject().getId().equals(request.projectId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "error.stage.project-mismatch", "Stage must belong to the selected project");
        }

        Worker worker = referenceDataService.getWorker(request.workerId());
        if (worker.getProjects().stream().noneMatch(project -> project.getId().equals(request.projectId()))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "error.worker.project-required", "Worker must be attached to the selected project");
        }

        payment.setWorker(worker);
        payment.setStage(stage);
        payment.setAmount(request.amount());
        payment.setPaymentDate(request.paymentDate());
    }

    private void syncImages(WorkerPayment payment, List<Long> retainedDocumentIds, List<MultipartFile> documentFiles) {
        List<WorkerPaymentImage> images = payment.getImages();
        Map<Long, WorkerPaymentImage> existingById = images.stream()
                .filter(image -> image.getId() != null)
                .collect(Collectors.toMap(WorkerPaymentImage::getId, Function.identity()));
        List<Long> retainedIds = retainedDocumentIds == null
                ? images.stream().map(WorkerPaymentImage::getId).filter(Objects::nonNull).toList()
                : retainedDocumentIds.stream().filter(Objects::nonNull).distinct().toList();

        for (Long documentId : retainedIds) {
            if (!existingById.containsKey(documentId)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "error.document.not-attached", "Document is not attached to this record");
            }
        }

        Set<Long> retainedIdSet = new HashSet<>(retainedIds);
        List<String> removedPublicIds = images.stream()
                .filter(image -> image.getId() != null && !retainedIdSet.contains(image.getId()))
                .map(WorkerPaymentImage::getPublicId)
                .filter(publicId -> publicId != null && !publicId.isBlank())
                .toList();
        List<DocumentStorageService.DocumentUploadResponse> uploadedDocuments = new ArrayList<>();

        try {
            uploadedDocuments = documentStorageService.storeImages(documentFiles);
            registerUploadedDocumentRollbackCleanup(uploadedDocuments.stream()
                    .map(DocumentStorageService.DocumentUploadResponse::publicId)
                    .toList());

            List<WorkerPaymentImage> nextImages = new ArrayList<>();
            for (Long retainedId : retainedIds) {
                WorkerPaymentImage image = existingById.get(retainedId);
                image.setSortOrder(nextImages.size());
                nextImages.add(image);
            }
            for (DocumentStorageService.DocumentUploadResponse uploadedDocument : uploadedDocuments) {
                WorkerPaymentImage image = new WorkerPaymentImage();
                image.setPayment(payment);
                image.setImageUrl(uploadedDocument.path());
                image.setPublicId(uploadedDocument.publicId());
                image.setOriginalFileName(uploadedDocument.originalFileName());
                image.setContentType(uploadedDocument.contentType());
                image.setSizeBytes(uploadedDocument.size());
                image.setSortOrder(nextImages.size());
                nextImages.add(image);
            }

            images.clear();
            images.addAll(nextImages);
            registerRemovedDocumentCommitCleanup(removedPublicIds);
        } catch (RuntimeException exception) {
            documentStorageService.deleteImagesQuietly(uploadedDocuments.stream()
                    .map(DocumentStorageService.DocumentUploadResponse::publicId)
                    .toList());
            throw exception;
        }
    }

    private void registerUploadedDocumentRollbackCleanup(List<String> publicIds) {
        List<String> cleanPublicIds = cleanPublicIds(publicIds);
        if (cleanPublicIds.isEmpty()) {
            return;
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) {
                    documentStorageService.deleteImagesQuietly(cleanPublicIds);
                }
            }
        });
    }

    private void registerRemovedDocumentCommitCleanup(List<String> publicIds) {
        List<String> cleanPublicIds = cleanPublicIds(publicIds);
        if (cleanPublicIds.isEmpty()) {
            return;
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            documentStorageService.deleteImagesQuietly(cleanPublicIds);
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                documentStorageService.deleteImagesQuietly(cleanPublicIds);
            }
        });
    }

    private List<String> cleanPublicIds(List<String> publicIds) {
        return (publicIds == null ? List.<String>of() : publicIds).stream()
                .filter(publicId -> publicId != null && !publicId.isBlank())
                .distinct()
                .toList();
    }

    private List<String> imagePublicIds(WorkerPayment payment) {
        return (payment.getImages() == null ? List.<WorkerPaymentImage>of() : payment.getImages()).stream()
                .map(WorkerPaymentImage::getPublicId)
                .toList();
    }

    private WorkerDtos.WorkerResponse toWorkerResponse(Worker worker) {
        Map<Long, BigDecimal> paidByWorkerId = workerPaymentRepository.sumAmountsByWorkerIds(List.of(worker.getId())).stream()
                .collect(java.util.stream.Collectors.toMap(
                        WorkerPaymentRepository.WorkerPaymentTotal::getWorkerId,
                        WorkerPaymentRepository.WorkerPaymentTotal::getTotalAmount
                ));
        Map<Long, List<WorkerStageBudget>> stageBudgetsByWorkerId = workerStageBudgetRepository.findDetailedByWorkerIds(List.of(worker.getId())).stream()
                .collect(java.util.stream.Collectors.groupingBy(item -> item.getWorker().getId()));
        return toWorkerResponse(worker, paidByWorkerId.getOrDefault(worker.getId(), BigDecimal.ZERO), stageBudgetsByWorkerId.getOrDefault(worker.getId(), Collections.emptyList()));
    }

    private List<WorkerDtos.WorkerResponse> toWorkerResponses(List<Worker> workers) {
        if (workers.isEmpty()) {
            return List.of();
        }
        List<Long> workerIds = workers.stream().map(Worker::getId).toList();
        Map<Long, BigDecimal> paidByWorkerId = workerPaymentRepository.sumAmountsByWorkerIds(workerIds).stream()
                .collect(java.util.stream.Collectors.toMap(
                        WorkerPaymentRepository.WorkerPaymentTotal::getWorkerId,
                        WorkerPaymentRepository.WorkerPaymentTotal::getTotalAmount
                ));
        Map<Long, List<WorkerStageBudget>> stageBudgetsByWorkerId = workerStageBudgetRepository.findDetailedByWorkerIds(workerIds).stream()
                .collect(java.util.stream.Collectors.groupingBy(item -> item.getWorker().getId()));
        return workers.stream()
                .map(worker -> toWorkerResponse(
                        worker,
                        paidByWorkerId.getOrDefault(worker.getId(), BigDecimal.ZERO),
                        stageBudgetsByWorkerId.getOrDefault(worker.getId(), Collections.emptyList())
                ))
                .toList();
    }

    private WorkerDtos.WorkerResponse toWorkerResponse(Worker worker, BigDecimal paid, List<WorkerStageBudget> workerStageBudgets) {
        List<WorkerDtos.WorkerStageBudgetResponse> stageBudgets = workerStageBudgets.stream()
                .map(item -> new WorkerDtos.WorkerStageBudgetResponse(
                        item.getStage().getId(),
                        item.getStage().getName(),
                        item.getPlannedBudget()
                ))
                .toList();
        List<Project> projects = worker.getProjects().stream()
                .sorted(Comparator.comparing(Project::getName))
                .toList();
        Long primaryProjectId = projects.stream().findFirst().map(Project::getId).orElse(null);
        return new WorkerDtos.WorkerResponse(
                worker.getId(),
                worker.getName(),
                worker.getType(),
                primaryProjectId,
                projects.stream().map(Project::getId).toList(),
                projects.stream().map(Project::getName).toList(),
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
                documents(payment)
        );
    }

    private List<DocumentDtos.DocumentAttachmentResponse> documents(WorkerPayment payment) {
        return (payment.getImages() == null ? List.<WorkerPaymentImage>of() : payment.getImages()).stream()
                .map(image -> new DocumentDtos.DocumentAttachmentResponse(
                        image.getId(),
                        image.getImageUrl(),
                        image.getPublicId(),
                        image.getOriginalFileName(),
                        image.getContentType(),
                        image.getSizeBytes(),
                        image.getSortOrder()
                ))
                .toList();
    }
}
