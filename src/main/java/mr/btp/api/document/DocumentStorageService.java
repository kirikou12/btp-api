package mr.btp.api.document;

import mr.btp.api.common.exception.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class DocumentStorageService {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(".jpg", ".jpeg", ".png", ".webp", ".heic");

    private final Path uploadDirectory;

    public DocumentStorageService(@Value("${app.documents.upload-dir:./data/uploads}") String uploadDirectory) {
        this.uploadDirectory = Path.of(uploadDirectory).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.uploadDirectory);
        } catch (IOException exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not initialize document storage");
        }
    }

    public DocumentUploadResponse storeImage(MultipartFile file) {
        if (file.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Uploaded file is empty");
        }

        String contentType = file.getContentType();
        if (contentType == null || !contentType.toLowerCase(Locale.ROOT).startsWith("image/")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Only image uploads are supported");
        }

        String extension = getAllowedExtension(file.getOriginalFilename(), contentType);
        String fileName = UUID.randomUUID() + extension;
        Path targetFile = uploadDirectory.resolve(fileName).normalize();

        try (InputStream inputStream = file.getInputStream()) {
            Files.copy(inputStream, targetFile, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not store uploaded image");
        }

        return new DocumentUploadResponse("/api/uploads/" + fileName, fileName, contentType, file.getSize());
    }

    public Resource load(String fileName) {
        String safeFileName = sanitizeFileName(fileName);
        Path targetFile = uploadDirectory.resolve(safeFileName).normalize();
        if (!targetFile.startsWith(uploadDirectory) || !Files.exists(targetFile)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Document not found");
        }

        try {
            return new UrlResource(targetFile.toUri());
        } catch (MalformedURLException exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not read stored document");
        }
    }

    public String detectContentType(String fileName) {
        String safeFileName = sanitizeFileName(fileName);
        Path targetFile = uploadDirectory.resolve(safeFileName).normalize();
        try {
            return Files.probeContentType(targetFile);
        } catch (IOException exception) {
            return null;
        }
    }

    private String getAllowedExtension(String originalFileName, String contentType) {
        String extension = extractExtension(originalFileName);
        if (ALLOWED_EXTENSIONS.contains(extension)) {
            return extension;
        }

        return switch (contentType.toLowerCase(Locale.ROOT)) {
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            case "image/heic", "image/heif" -> ".heic";
            default -> throw new ApiException(HttpStatus.BAD_REQUEST, "Unsupported image type");
        };
    }

    private String extractExtension(String originalFileName) {
        if (originalFileName == null || !originalFileName.contains(".")) {
            return "";
        }

        return originalFileName.substring(originalFileName.lastIndexOf('.')).toLowerCase(Locale.ROOT);
    }

    private String sanitizeFileName(String fileName) {
        String safeFileName = Path.of(fileName).getFileName().toString();
        if (!safeFileName.equals(fileName)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid document path");
        }
        return safeFileName;
    }

    public record DocumentUploadResponse(String path, String fileName, String contentType, long size) {
    }
}
