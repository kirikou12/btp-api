package mr.btp.api.project;

import mr.btp.api.common.exception.ApiException;
import mr.btp.api.common.service.ReferenceDataService;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final ConstructionStageRepository stageRepository;
    private final ReferenceDataService referenceDataService;

    public ProjectService(ProjectRepository projectRepository,
                          ConstructionStageRepository stageRepository,
                          ReferenceDataService referenceDataService) {
        this.projectRepository = projectRepository;
        this.stageRepository = stageRepository;
        this.referenceDataService = referenceDataService;
    }

    @Transactional(readOnly = true)
    public Page<ProjectDtos.ProjectResponse> list(int page, int size) {
        return projectRepository.findAll(PageRequest.of(page, size)).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public ProjectDtos.ProjectResponse get(Long id) {
        return toResponse(referenceDataService.getProject(id));
    }

    @Transactional
    public ProjectDtos.ProjectResponse create(ProjectDtos.ProjectRequest request) {
        Project project = new Project();
        apply(project, request);
        Project saved = projectRepository.save(project);
        return toResponse(saved);
    }

    @Transactional
    public ProjectDtos.ProjectResponse update(Long id, ProjectDtos.ProjectRequest request) {
        Project project = referenceDataService.getProject(id);
        apply(project, request);
        return toResponse(projectRepository.save(project));
    }

    @Transactional
    public void delete(Long id) {
        referenceDataService.getProject(id);
        projectRepository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public List<ProjectDtos.StageResponse> listStages(Long projectId) {
        referenceDataService.getProject(projectId);
        return referenceDataService.stagesByProject(projectId).stream().map(this::toStageResponse).toList();
    }

    @Transactional
    public ProjectDtos.StageResponse createStage(Long projectId, ProjectDtos.StageCreateRequest request) {
        Project project = referenceDataService.getProject(projectId);

        String stageName = request.name().trim();
        stageRepository.findByProjectIdAndNameIgnoreCase(projectId, stageName).ifPresent(s -> {
            throw new ApiException(HttpStatus.CONFLICT, "Stage with name '" + stageName + "' already exists for this project");
        });

        ConstructionStage stage = new ConstructionStage();
        stage.setProject(project);
        stage.setName(stageName);
        stage.setSortOrder(nextSortOrder(projectId));
        stage.setStatus(StageStatus.NOT_STARTED);
        stage.setPlannedBudget(request.plannedBudget());
        stage.setProgressPercent(0);
        return toStageResponse(stageRepository.save(stage));
    }

    @Transactional
    public ProjectDtos.StageResponse updateStage(Long stageId, ProjectDtos.StageRequest request) {
        ConstructionStage stage = referenceDataService.getStage(stageId);

        if (request.name() != null && !request.name().isBlank()) {
            String newName = request.name().trim();
            if (!stage.getName().equalsIgnoreCase(newName)) {
                stageRepository.findByProjectIdAndNameIgnoreCase(stage.getProject().getId(), newName).ifPresent(s -> {
                    throw new ApiException(HttpStatus.CONFLICT, "Stage with name '" + newName + "' already exists for this project");
                });
            }
        }

        apply(stage, request);
        return toStageResponse(stageRepository.save(stage));
    }

    @Transactional
    public void deleteStage(Long stageId) {
        referenceDataService.getStage(stageId);
        referenceDataService.validateStageDeletion(stageId);
        stageRepository.deleteById(stageId);
    }

    private void apply(Project project, ProjectDtos.ProjectRequest request) {
        project.setName(request.name().trim());
        project.setLocation(request.location().trim());
        project.setDescription(request.description());
        project.setStartDate(request.startDate());
        project.setEstimatedSalePrice(request.estimatedSalePrice());
        project.setBudget(request.budget());
        project.setStatus(request.status() == null ? ProjectStatus.PLANNING : request.status());
    }

    private void apply(ConstructionStage stage, ProjectDtos.StageRequest request) {
        if (request.name() != null && !request.name().isBlank()) {
            stage.setName(request.name().trim());
        }
        if (request.sortOrder() != null) {
            stage.setSortOrder(request.sortOrder());
        }
        if (request.startDate() != null && request.endDate() != null && request.endDate().isBefore(request.startDate())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Stage end date cannot be before start date");
        }
        stage.setStatus(request.status());
        stage.setStartDate(request.startDate());
        stage.setEndDate(request.endDate());
        stage.setPlannedBudget(request.plannedBudget());

        if (request.status() == StageStatus.NOT_STARTED) {
            stage.setProgressPercent(0);
            return;
        }

        if (request.status() == StageStatus.COMPLETED) {
            stage.setProgressPercent(100);
            return;
        }

        stage.setProgressPercent(request.progressPercent() == null ? stage.getProgressPercent() : request.progressPercent());
    }

    private int nextSortOrder(Long projectId) {
        return referenceDataService.stagesByProject(projectId).stream()
                .map(ConstructionStage::getSortOrder)
                .max(Integer::compareTo)
                .orElse(0) + 1;
    }

    private ProjectDtos.ProjectResponse toResponse(Project project) {
        return new ProjectDtos.ProjectResponse(
                project.getId(),
                project.getName(),
                project.getLocation(),
                project.getDescription(),
                project.getStartDate(),
                project.getEstimatedSalePrice(),
                project.getBudget(),
                project.getStatus().name(),
                project.getCreatedAt(),
                project.getUpdatedAt()
        );
    }

    private ProjectDtos.StageResponse toStageResponse(ConstructionStage stage) {
        return new ProjectDtos.StageResponse(
                stage.getId(),
                stage.getProject().getId(),
                stage.getName(),
                stage.getSortOrder(),
                stage.getStatus().name(),
                stage.getStartDate(),
                stage.getEndDate(),
                stage.getPlannedBudget(),
                referenceDataService.stageActualCost(stage.getId()),
                stage.getProgressPercent(),
                referenceDataService.isStageDeletable(stage.getId())
        );
    }
}
