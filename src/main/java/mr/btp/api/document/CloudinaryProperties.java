package mr.btp.api.document;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.cloudinary")
public class CloudinaryProperties {

    private String cloudName;
    private String apiKey;
    private String apiSecret;
    private String folder = "btp/documents";

    public String getCloudName() {
        return cloudName;
    }

    public void setCloudName(String cloudName) {
        this.cloudName = cloudName;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getApiSecret() {
        return apiSecret;
    }

    public void setApiSecret(String apiSecret) {
        this.apiSecret = apiSecret;
    }

    public String getFolder() {
        return folder;
    }

    public void setFolder(String folder) {
        this.folder = folder;
    }

    public boolean isConfigured() {
        return hasText(cloudName) && hasText(apiKey) && hasText(apiSecret);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
