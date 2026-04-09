package mr.btp.api.consumption;

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
public class MaterialConsumptionController {

    private final MaterialConsumptionService materialConsumptionService;

    public MaterialConsumptionController(MaterialConsumptionService materialConsumptionService) {
        this.materialConsumptionService = materialConsumptionService;
    }

    @GetMapping("/api/projects/{id}/consumptions")
    public List<ConsumptionDtos.ConsumptionResponse> listByProject(@PathVariable Long id) {
        return materialConsumptionService.byProject(id);
    }

    @PostMapping("/api/material-consumptions")
    public ConsumptionDtos.ConsumptionResponse create(@Valid @RequestBody ConsumptionDtos.ConsumptionRequest request) {
        return materialConsumptionService.create(request);
    }

    @GetMapping("/api/material-consumptions/{id}")
    public ConsumptionDtos.ConsumptionResponse get(@PathVariable Long id) {
        return materialConsumptionService.get(id);
    }

    @PutMapping("/api/material-consumptions/{id}")
    public ConsumptionDtos.ConsumptionResponse update(@PathVariable Long id,
                                                      @Valid @RequestBody ConsumptionDtos.ConsumptionRequest request) {
        return materialConsumptionService.update(id, request);
    }

    @DeleteMapping("/api/material-consumptions/{id}")
    public void delete(@PathVariable Long id) {
        materialConsumptionService.delete(id);
    }
}
