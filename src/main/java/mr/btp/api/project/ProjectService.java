package mr.btp.api.project;

import mr.btp.api.common.service.ReferenceDataService;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final ConstructionStageRepository stageRepository;
    private final StageTemplateRepository stageTemplateRepository;
    private final ReferenceDataService referenceDataService;

    public ProjectService(ProjectRepository projectRepository,
                          ConstructionStageRepository stageRepository,
                          StageTemplateRepository stageTemplateRepository,
                          ReferenceDataService referenceDataService) {
        this.projectRepository = projectRepository;
        this.stageRepository = stageRepository;
        this.stageTemplateRepository = stageTemplateRepository;
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
        Project saved = projectRepository.save(project);
        createStagesFromActiveTemplates(saved);
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

    public List<ProjectDtos.StageResponse> listStages(Long projectId) {
        referenceDataService.getProject(projectId);
        return referenceDataService.stagesByProject(projectId).stream().map(this::toStageResponse).toList();
    }

    @Transactional
    public ProjectDtos.StageResponse updateStage(Long stageId, ProjectDtos.StageRequest request) {
        ConstructionStage stage = referenceDataService.getStage(stageId);
        apply(stage, request);
        return toStageResponse(stageRepository.save(stage));
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
        stage.setStatus(request.status());
        stage.setStartDate(request.startDate());
        stage.setEndDate(request.endDate());
        stage.setPlannedBudget(request.plannedBudget());
        stage.setProgressPercent(request.progressPercent() == null ? stage.getProgressPercent() : request.progressPercent());
    }

    private void createStagesFromActiveTemplates(Project project) {
        List<StageTemplate> templates = stageTemplateRepository.findByActiveTrueOrderBySortOrderAsc();
        for (StageTemplate template : templates) {
            ConstructionStage stage = new ConstructionStage();
            stage.setProject(project);
            stage.setStageTemplate(template);
            stage.setName(template.getName());
            stage.setSortOrder(template.getSortOrder());
            stage.setStatus(StageStatus.NOT_STARTED);
            stage.setProgressPercent(0);
            stageRepository.save(stage);
        }
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
                stage.getStageTemplate() == null ? null : stage.getStageTemplate().getId(),
                stage.getName(),
                stage.getSortOrder(),
                stage.getStatus().name(),
                stage.getStartDate(),
                stage.getEndDate(),
                stage.getPlannedBudget(),
                referenceDataService.stageActualCost(stage.getId()),
                stage.getProgressPercent()
        );
    }
}
