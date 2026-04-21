package mr.btp.api.project;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@WithMockUser
class ProjectStageValidationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private ConstructionStageRepository stageRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void shouldFailWhenCreatingStageWithDuplicateName() throws Exception {
        Project project = createProject("Test Project");
        
        ProjectDtos.StageCreateRequest request = new ProjectDtos.StageCreateRequest("Foundation", new BigDecimal("1000.00"));
        
        // First creation should succeed
        mockMvc.perform(post("/api/projects/" + project.getId() + "/stages")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        // Second creation with same name should fail
        mockMvc.perform(post("/api/projects/" + project.getId() + "/stages")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
    }

    @Test
    void shouldFailWhenUpdatingStageToDuplicateName() throws Exception {
        Project project = createProject("Test Project Update");
        
        ConstructionStage stage1 = createStage(project, "Stage 1");
        ConstructionStage stage2 = createStage(project, "Stage 2");
        
        ProjectDtos.StageRequest updateRequest = new ProjectDtos.StageRequest(
                "Stage 2", // Duplicate name
                StageStatus.NOT_STARTED,
                null,
                null,
                new BigDecimal("500.00"),
                0,
                1
        );

        mockMvc.perform(put("/api/project-stages/" + stage1.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isConflict());
    }

    @Test
    void shouldSucceedWhenUpdatingStageToSameName() throws Exception {
        Project project = createProject("Test Project Same Name");
        ConstructionStage stage = createStage(project, "Unique Name");

        ProjectDtos.StageRequest updateRequest = new ProjectDtos.StageRequest(
                "Unique Name", // Same name as before
                StageStatus.IN_PROGRESS,
                null,
                null,
                new BigDecimal("500.00"),
                10,
                1
        );

        mockMvc.perform(put("/api/project-stages/" + stage.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk());
    }

    private Project createProject(String name) {
        Project project = new Project();
        project.setName(name);
        project.setLocation("Location");
        project.setStartDate(LocalDate.now());
        project.setEstimatedSalePrice(new BigDecimal("100000"));
        project.setBudget(new BigDecimal("80000"));
        project.setStatus(ProjectStatus.PLANNING);
        return projectRepository.save(project);
    }

    private ConstructionStage createStage(Project project, String name) {
        ConstructionStage stage = new ConstructionStage();
        stage.setProject(project);
        stage.setName(name);
        stage.setStatus(StageStatus.NOT_STARTED);
        stage.setSortOrder(1);
        stage.setPlannedBudget(new BigDecimal("1000"));
        stage.setProgressPercent(0);
        return stageRepository.save(stage);
    }
}
