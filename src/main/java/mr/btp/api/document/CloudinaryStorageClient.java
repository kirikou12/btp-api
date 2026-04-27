package mr.btp.api.document;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import mr.btp.api.common.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Service
public class CloudinaryStorageClient implements DocumentUploadClient {

    private final CloudinaryProperties properties;
    private Cloudinary cloudinary;

    public CloudinaryStorageClient(CloudinaryProperties properties) {
        this.properties = properties;
    }

    @Override
    public CloudinaryUpload uploadImage(byte[] content, String originalFileName, String contentType) {
        if (!properties.isConfigured()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "error.cloudinary.not-configured", "Cloudinary credentials are not configured");
        }

        Map<String, Object> options = new HashMap<>();
        options.put("resource_type", "image");
        options.put("secure", true);
        options.put("overwrite", false);
        options.put("unique_filename", true);
        if (hasText(properties.getFolder())) {
            options.put("folder", properties.getFolder().trim());
        }
        if (hasText(originalFileName)) {
            options.put("filename_override", originalFileName.trim());
        }

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> result = cloudinary().uploader().upload(content, options);
            return new CloudinaryUpload(
                    requiredString(result, "secure_url"),
                    requiredString(result, "public_id"),
                    asString(result.get("resource_type")),
                    asString(result.get("format")),
                    asLong(result.get("bytes"))
            );
        } catch (IOException | RuntimeException exception) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "error.document.store-failed", "Could not store uploaded image");
        }
    }

    @Override
    public void deleteImage(String publicId) {
        if (!hasText(publicId)) {
            return;
        }
        if (!properties.isConfigured()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "error.cloudinary.not-configured", "Cloudinary credentials are not configured");
        }

        try {
            cloudinary().uploader().destroy(publicId.trim(), ObjectUtils.asMap(
                    "resource_type", "image",
                    "invalidate", true
            ));
        } catch (IOException | RuntimeException exception) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "error.document.delete-failed", "Could not delete uploaded image");
        }
    }

    private synchronized Cloudinary cloudinary() {
        if (cloudinary == null) {
            cloudinary = new Cloudinary(ObjectUtils.asMap(
                    "cloud_name", properties.getCloudName(),
                    "api_key", properties.getApiKey(),
                    "api_secret", properties.getApiSecret(),
                    "secure", true
            ));
        }
        return cloudinary;
    }

    private String requiredString(Map<String, Object> result, String key) {
        String value = asString(result.get(key));
        if (!hasText(value)) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "error.document.store-failed", "Could not store uploaded image");
        }
        return value;
    }

    private String asString(Object value) {
        return value == null ? null : value.toString();
    }

    private long asLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String stringValue && !stringValue.isBlank()) {
            return Long.parseLong(stringValue);
        }
        return 0;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    public record CloudinaryUpload(String secureUrl, String publicId, String resourceType, String format, long size) {
    }
}
