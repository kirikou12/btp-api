package mr.btp.api.project;

import mr.btp.api.common.exception.ApiException;
import mr.btp.api.common.service.ReferenceDataService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

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

    public Page<ProjectDtos.ProjectResponse> list(int page, int size) {
        return projectRepository.findAll(PageRequest.of(page, size)).map(this::toResponse);
    }

    public ProjectDtos.ProjectResponse get(Long id) {
        return toResponse(referenceDataService.getProject(id));
    }

    @Transactional
    public ProjectDtos.ProjectResponse create(ProjectDtos.ProjectRequest request) {
        Project project = new Project();
        apply(project, request);
        return toResponse(projectRepository.save(project));
    }

    @Transactional
    public ProjectDtos.ProjectResponse update(Long id, ProjectDtos.ProjectRequest request) {
        Project project = referenceDataService.getProject(id);
        apply(project, request);
        return toResponse(projectRepository.save(project));
    }

    @Transactional
    public void delete(Long id) {
        if (!projectRepository.existsById(id)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Project not found");
        }
        projectRepository.deleteById(id);
    }

    public List<ProjectDtos.StageResponse> listStages(Long projectId) {
        referenceDataService.getProject(projectId);
        return referenceDataService.stagesByProject(projectId).stream().map(this::toStageResponse).toList();
    }

    @Transactional
    public ProjectDtos.StageResponse createStage(Long projectId, ProjectDtos.StageRequest request) {
        ConstructionStage stage = new ConstructionStage();
        stage.setProject(referenceDataService.getProject(projectId));
        apply(stage, request);
        return toStageResponse(stageRepository.save(stage));
    }

    @Transactional
    public ProjectDtos.StageResponse updateStage(Long stageId, ProjectDtos.StageRequest request) {
        ConstructionStage stage = referenceDataService.getStage(stageId);
        apply(stage, request);
        return toStageResponse(stageRepository.save(stage));
    }

    @Transactional
    public void deleteStage(Long stageId) {
        if (!stageRepository.existsById(stageId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Stage not found");
        }
        stageRepository.deleteById(stageId);
    }

    private void apply(Project project, ProjectDtos.ProjectRequest request) {
        project.setName(request.name().trim());
        project.setLocation(request.location().trim());
        project.setDescription(request.description());
        project.setStartDate(request.startDate());
        project.setEstimatedSalePrice(request.estimatedSalePrice());
        project.setBudget(request.budget());
        project.setStatus(request.status());
    }

    private void apply(ConstructionStage stage, ProjectDtos.StageRequest request) {
        stage.setName(request.name().trim());
        stage.setSortOrder(request.sortOrder());
        stage.setStatus(request.status());
        stage.setStartDate(request.startDate());
        stage.setEndDate(request.endDate());
        stage.setPlannedBudget(request.plannedBudget());
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
                stage.getPlannedBudget()
        );
    }
}
