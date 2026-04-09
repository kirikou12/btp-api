package mr.btp.api.project;

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
public class StageTemplateController {

    private final StageTemplateService stageTemplateService;

    public StageTemplateController(StageTemplateService stageTemplateService) {
        this.stageTemplateService = stageTemplateService;
    }

    @GetMapping("/api/stage-templates")
    public List<ProjectDtos.StageTemplateResponse> list() {
        return stageTemplateService.list();
    }

    @PostMapping("/api/stage-templates")
    public ProjectDtos.StageTemplateResponse create(@Valid @RequestBody ProjectDtos.StageTemplateRequest request) {
        return stageTemplateService.create(request);
    }

    @PutMapping("/api/stage-templates/{id}")
    public ProjectDtos.StageTemplateResponse update(@PathVariable Long id, @Valid @RequestBody ProjectDtos.StageTemplateRequest request) {
        return stageTemplateService.update(id, request);
    }

    @DeleteMapping("/api/stage-templates/{id}")
    public void delete(@PathVariable Long id) {
        stageTemplateService.delete(id);
    }
}
