package mr.btp.api.project;

import jakarta.validation.Valid;
import java.util.List;
import mr.btp.api.common.dto.PageResponse;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ProjectController {

    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @GetMapping("/api/projects")
    public PageResponse<ProjectDtos.ProjectResponse> list(@RequestParam(defaultValue = "0") int page,
                                                          @RequestParam(defaultValue = "20") int size) {
        return PageResponse.from(projectService.list(page, size));
    }

    @PostMapping("/api/projects")
    public ProjectDtos.ProjectResponse create(@Valid @RequestBody ProjectDtos.ProjectRequest request) {
        return projectService.create(request);
    }

    @GetMapping("/api/projects/{id}")
    public ProjectDtos.ProjectResponse get(@PathVariable Long id) {
        return projectService.get(id);
    }

    @PutMapping("/api/projects/{id}")
    public ProjectDtos.ProjectResponse update(@PathVariable Long id, @Valid @RequestBody ProjectDtos.ProjectRequest request) {
        return projectService.update(id, request);
    }

    @DeleteMapping("/api/projects/{id}")
    public void delete(@PathVariable Long id) {
        projectService.delete(id);
    }

    @GetMapping("/api/projects/{id}/stages")
    public List<ProjectDtos.StageResponse> listStages(@PathVariable Long id) {
        return projectService.listStages(id);
    }

    @PostMapping("/api/projects/{id}/stages")
    public ProjectDtos.StageResponse createStage(@PathVariable Long id, @Valid @RequestBody ProjectDtos.StageRequest request) {
        return projectService.createStage(id, request);
    }

    @PutMapping("/api/stages/{id}")
    public ProjectDtos.StageResponse updateStage(@PathVariable Long id, @Valid @RequestBody ProjectDtos.StageRequest request) {
        return projectService.updateStage(id, request);
    }

    @DeleteMapping("/api/stages/{id}")
    public void deleteStage(@PathVariable Long id) {
        projectService.deleteStage(id);
    }
}
