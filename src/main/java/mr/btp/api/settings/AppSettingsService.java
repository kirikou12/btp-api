package mr.btp.api.settings;

import mr.btp.api.common.exception.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AppSettingsService {

    private static final String PROJECT_STAGE_MAX_IN_PROGRESS_PER_PROJECT_KEY = "project.stage.max-in-progress-per-project";
    private static final int MIN_PROJECT_STAGE_MAX_IN_PROGRESS = 1;
    private static final int MAX_PROJECT_STAGE_MAX_IN_PROGRESS = 100;

    private final JdbcTemplate jdbcTemplate;
    private final int defaultProjectStageMaxInProgressPerProject;

    public AppSettingsService(JdbcTemplate jdbcTemplate,
                              @Value("${app.project.stage.max-in-progress-per-project:6}") int defaultProjectStageMaxInProgressPerProject) {
        validateProjectStageMaxInProgressPerProject(defaultProjectStageMaxInProgressPerProject);
        this.jdbcTemplate = jdbcTemplate;
        this.defaultProjectStageMaxInProgressPerProject = defaultProjectStageMaxInProgressPerProject;
    }

    @Transactional(readOnly = true)
    public AppSettingsDtos.AppSettingsResponse get() {
        return new AppSettingsDtos.AppSettingsResponse(getProjectStageMaxInProgressPerProject());
    }

    @Transactional
    public AppSettingsDtos.AppSettingsResponse update(AppSettingsDtos.AppSettingsRequest request) {
        int nextValue = request.projectStageMaxInProgressPerProject();
        validateProjectStageMaxInProgressPerProject(nextValue);
        jdbcTemplate.update(
                """
                insert into app_settings (setting_key, setting_value)
                values (?, ?)
                on conflict (setting_key) do update
                set setting_value = excluded.setting_value,
                    updated_at = current_timestamp
                """,
                PROJECT_STAGE_MAX_IN_PROGRESS_PER_PROJECT_KEY,
                String.valueOf(nextValue)
        );
        return get();
    }

    @Transactional(readOnly = true)
    public int getProjectStageMaxInProgressPerProject() {
        try {
            Integer value = jdbcTemplate.queryForObject(
                    "select setting_value from app_settings where setting_key = ?",
                    (rs, rowNum) -> Integer.valueOf(rs.getString("setting_value")),
                    PROJECT_STAGE_MAX_IN_PROGRESS_PER_PROJECT_KEY
            );
            return value == null ? defaultProjectStageMaxInProgressPerProject : value;
        } catch (EmptyResultDataAccessException exception) {
            return defaultProjectStageMaxInProgressPerProject;
        }
    }

    private void validateProjectStageMaxInProgressPerProject(int value) {
        if (value < MIN_PROJECT_STAGE_MAX_IN_PROGRESS || value > MAX_PROJECT_STAGE_MAX_IN_PROGRESS) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "error.settings.project-stage-max-in-progress.range",
                    "Project stage in-progress limit must be between {0} and {1}.",
                    MIN_PROJECT_STAGE_MAX_IN_PROGRESS,
                    MAX_PROJECT_STAGE_MAX_IN_PROGRESS
            );
        }
    }
}
