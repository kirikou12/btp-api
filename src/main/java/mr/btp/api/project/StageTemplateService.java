package mr.btp.api.project;

import java.util.List;
import mr.btp.api.common.exception.ApiException;
import mr.btp.api.consumption.MaterialConsumptionRepository;
import mr.btp.api.expense.DirectExpenseRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StageTemplateService {

    private final StageTemplateRepository stageTemplateRepository;
    private final ConstructionStageRepository stageRepository;
    private final ProjectRepository projectRepository;
    private final DirectExpenseRepository expenseRepository;
    private final MaterialConsumptionRepository consumptionRepository;

    public StageTemplateService(StageTemplateRepository stageTemplateRepository,
                                ConstructionStageRepository stageRepository,
                                ProjectRepository projectRepository,
                                DirectExpenseRepository expenseRepository,
                                MaterialConsumptionRepository consumptionRepository) {
        this.stageTemplateRepository = stageTemplateRepository;
        this.stageRepository = stageRepository;
        this.projectRepository = projectRepository;
        this.expenseRepository = expenseRepository;
        this.consumptionRepository = consumptionRepository;
    }

    public List<ProjectDtos.StageTemplateResponse> list() {
        return stageTemplateRepository.findAllByOrderBySortOrderAsc().stream().map(this::toResponse).toList();
    }

    @Transactional
    public ProjectDtos.StageTemplateResponse create(ProjectDtos.StageTemplateRequest request) {
        ensureUniqueName(request.name(), null);
        StageTemplate template = new StageTemplate();
        apply(template, request);
        StageTemplate saved = stageTemplateRepository.save(template);
        if (saved.isActive()) {
            projectRepository.findAll().forEach(project -> stageRepository.save(createProjectStage(project, saved)));
        }
        return toResponse(saved);
    }

    @Transactional
    public ProjectDtos.StageTemplateResponse update(Long id, ProjectDtos.StageTemplateRequest request) {
        StageTemplate template = stageTemplateRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Stage template not found"));
        ensureUniqueName(request.name(), id);
        boolean wasActive = template.isActive();
        apply(template, request);
        StageTemplate saved = stageTemplateRepository.save(template);

        stageRepository.findByStageTemplateId(id).forEach(stage -> {
            stage.setName(saved.getName());
            stage.setSortOrder(saved.getSortOrder());
            stageRepository.save(stage);
        });

        if (!wasActive && saved.isActive()) {
            projectRepository.findAll().forEach(project -> {
                boolean exists = stageRepository.findByProjectIdOrderBySortOrderAsc(project.getId()).stream()
                        .anyMatch(stage -> stage.getStageTemplate() != null && stage.getStageTemplate().getId().equals(saved.getId()));
                if (!exists) {
                    stageRepository.save(createProjectStage(project, saved));
                }
            });
        }

        return toResponse(saved);
    }

    @Transactional
    public void delete(Long id) {
        StageTemplate template = stageTemplateRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Stage template not found"));
        List<ConstructionStage> linkedStages = stageRepository.findByStageTemplateId(id);
        boolean hasRecordedCosts = linkedStages.stream().anyMatch(stage ->
                expenseRepository.countByStageId(stage.getId()) > 0 || consumptionRepository.countByStageId(stage.getId()) > 0
        );
        if (hasRecordedCosts) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Cannot delete a stage template that already carries recorded costs. Deactivate it instead.");
        }
        stageRepository.deleteAll(linkedStages);
        stageTemplateRepository.delete(template);
    }

    private void apply(StageTemplate template, ProjectDtos.StageTemplateRequest request) {
        template.setName(request.name().trim());
        template.setSortOrder(request.sortOrder());
        template.setActive(request.active());
    }

    private void ensureUniqueName(String name, Long currentId) {
        stageTemplateRepository.findAll().stream()
                .filter(template -> currentId == null || !template.getId().equals(currentId))
                .filter(template -> template.getName().equalsIgnoreCase(name.trim()))
                .findFirst()
                .ifPresent(template -> {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "Stage template name already exists");
                });
    }

    private ConstructionStage createProjectStage(Project project, StageTemplate template) {
        ConstructionStage stage = new ConstructionStage();
        stage.setProject(project);
        stage.setStageTemplate(template);
        stage.setName(template.getName());
        stage.setSortOrder(template.getSortOrder());
        stage.setStatus(StageStatus.NOT_STARTED);
        stage.setProgressPercent(0);
        return stage;
    }

    private ProjectDtos.StageTemplateResponse toResponse(StageTemplate template) {
        return new ProjectDtos.StageTemplateResponse(
                template.getId(),
                template.getName(),
                template.getSortOrder(),
                template.isActive(),
                template.getCreatedAt(),
                template.getUpdatedAt()
        );
    }
}
