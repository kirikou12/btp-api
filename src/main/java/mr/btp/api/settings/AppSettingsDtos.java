package mr.btp.api.settings;

public final class AppSettingsDtos {

    private AppSettingsDtos() {
    }

    public record AppSettingsResponse(int projectStageMaxInProgressPerProject) {
    }

    public record AppSettingsRequest(int projectStageMaxInProgressPerProject) {
    }
}
