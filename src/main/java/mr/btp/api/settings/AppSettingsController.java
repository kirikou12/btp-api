package mr.btp.api.settings;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.access.prepost.PreAuthorize;

@RestController
public class AppSettingsController {

    private final AppSettingsService appSettingsService;

    public AppSettingsController(AppSettingsService appSettingsService) {
        this.appSettingsService = appSettingsService;
    }

    @GetMapping("/api/settings")
    public AppSettingsDtos.AppSettingsResponse get() {
        return appSettingsService.get();
    }

    @PutMapping("/api/settings")
    @PreAuthorize("hasRole('ADMIN')")
    public AppSettingsDtos.AppSettingsResponse update(@RequestBody AppSettingsDtos.AppSettingsRequest request) {
        return appSettingsService.update(request);
    }
}
