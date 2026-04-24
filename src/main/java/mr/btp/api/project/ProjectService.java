package mr.btp.api.project;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import mr.btp.api.common.exception.ApiException;
import mr.btp.api.common.i18n.MessageKey;
import mr.btp.api.common.service.ReferenceDataService;
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
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "error.project.stage-name-already-exists",
                    "Stage with name ''{0}'' already exists for this project",
                    stageName
            );
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
    public ProjectDtos.StageResponse updateStage(Long stageId, JsonNode request) {
        ConstructionStage stage = referenceDataService.getStage(stageId);

        if (hasNonNull(request, "name") && !request.get("name").asText().isBlank()) {
            String newName = request.get("name").asText().trim();
            if (!stage.getName().equalsIgnoreCase(newName)) {
                stageRepository.findByProjectIdAndNameIgnoreCase(stage.getProject().getId(), newName).ifPresent(s -> {
                    throw new ApiException(
                            HttpStatus.CONFLICT,
                            "error.project.stage-name-already-exists",
                            "Stage with name ''{0}'' already exists for this project",
                            newName
                    );
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

    private void apply(ConstructionStage stage, JsonNode request) {
        if (hasNonNull(request, "name") && !request.get("name").asText().isBlank()) {
            stage.setName(request.get("name").asText().trim());
        }
        if (hasNonNull(request, "sortOrder")) {
            int sortOrder = readInteger(request, "sortOrder");
            if (sortOrder < 1) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "error.project.stage.sort-order.min", "Stage sort order must be at least 1");
            }
            stage.setSortOrder(sortOrder);
        }

        StageStatus nextStatus = hasNonNull(request, "status") ? parseStageStatus(request.get("status").asText()) : stage.getStatus();
        LocalDate nextStartDate = readDate(request, "startDate", stage.getStartDate());
        LocalDate nextEndDate = readDate(request, "endDate", stage.getEndDate());
        BigDecimal nextPlannedBudget = readBigDecimal(request, "plannedBudget", stage.getPlannedBudget());

        if (nextStartDate != null && nextEndDate != null && nextEndDate.isBefore(nextStartDate)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "error.project.stage.end-before-start", "Stage end date cannot be before start date");
        }
        stage.setStatus(nextStatus);
        stage.setStartDate(nextStartDate);
        stage.setEndDate(nextEndDate);
        stage.setPlannedBudget(nextPlannedBudget);

        if (nextStatus == StageStatus.NOT_STARTED) {
            stage.setProgressPercent(0);
            return;
        }

        if (nextStatus == StageStatus.COMPLETED) {
            stage.setProgressPercent(100);
            return;
        }

        if (hasNonNull(request, "progressPercent")) {
            int progressPercent = readInteger(request, "progressPercent");
            if (progressPercent < 0 || progressPercent > 100) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "error.project.stage.progress-percent.range", "Stage progress percent must be between 0 and 100");
            }
            stage.setProgressPercent(progressPercent);
        }
    }

    private boolean hasNonNull(JsonNode request, String field) {
        return request != null && request.has(field) && !request.get(field).isNull();
    }

    private StageStatus parseStageStatus(String value) {
        try {
            return StageStatus.valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "error.project.stage.status.unsupported", "Unsupported stage status");
        }
    }

    private LocalDate readDate(JsonNode request, String field, LocalDate fallback) {
        if (request == null || !request.has(field)) {
            return fallback;
        }
        if (request.get(field).isNull() || request.get(field).asText().isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(request.get(field).asText());
        } catch (RuntimeException exception) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "error.project.stage-field.iso-date",
                    "Stage {0} must be an ISO date",
                    stageField(field)
            );
        }
    }

    private int readInteger(JsonNode request, String field) {
        JsonNode value = request.get(field);
        if (!value.canConvertToInt()) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "error.project.stage-field.integer",
                    "Stage {0} must be an integer",
                    stageField(field)
            );
        }
        return value.asInt();
    }

    private MessageKey stageField(String field) {
        return switch (field) {
            case "startDate" -> MessageKey.of("field.project-stage.start-date", "start date");
            case "endDate" -> MessageKey.of("field.project-stage.end-date", "end date");
            case "progressPercent" -> MessageKey.of("field.project-stage.progress-percent", "progress percent");
            case "plannedBudget" -> MessageKey.of("field.project-stage.planned-budget", "planned budget");
            case "sortOrder" -> MessageKey.of("field.project-stage.sort-order", "sort order");
            case "status" -> MessageKey.of("field.project-stage.status", "status");
            default -> MessageKey.of("field.project-stage." + field, field);
        };
    }

    private BigDecimal readBigDecimal(JsonNode request, String field, BigDecimal fallback) {
        if (request == null || !request.has(field)) {
            return fallback;
        }
        if (request.get(field).isNull()) {
            return null;
        }
        if (!request.get(field).isNumber()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "error.project.stage.planned-budget.numeric", "Stage planned budget must be numeric");
        }
        try {
            BigDecimal value = request.get(field).decimalValue();
            if (value.compareTo(BigDecimal.ZERO) < 0) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "error.project.stage.planned-budget.negative", "Stage planned budget cannot be negative");
            }
            return value;
        } catch (NumberFormatException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "error.project.stage.planned-budget.numeric", "Stage planned budget must be numeric");
        }
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
