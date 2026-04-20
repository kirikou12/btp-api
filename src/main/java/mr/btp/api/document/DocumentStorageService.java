package mr.btp.api.document;

import mr.btp.api.common.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class DocumentStorageService {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(".jpg", ".jpeg", ".png", ".webp", ".heic");

    private final UploadedDocumentRepository uploadedDocumentRepository;

    public DocumentStorageService(UploadedDocumentRepository uploadedDocumentRepository) {
        this.uploadedDocumentRepository = uploadedDocumentRepository;
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
        String storedContentType = getStoredContentType(contentType, extension);
        String fileName = UUID.randomUUID() + extension;

        try {
            UploadedDocument document = new UploadedDocument();
            document.setFileName(fileName);
            document.setOriginalFileName(sanitizeOriginalFileName(file.getOriginalFilename()));
            document.setContentType(storedContentType);
            document.setSizeBytes(file.getSize());
            document.setContent(file.getBytes());
            uploadedDocumentRepository.save(document);
        } catch (IOException exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not store uploaded image");
        }

        return new DocumentUploadResponse("/api/uploads/" + fileName, fileName, storedContentType, file.getSize());
    }

    public StoredDocument load(String fileName) {
        String safeFileName = sanitizeFileName(fileName);
        UploadedDocument document = uploadedDocumentRepository.findByFileName(safeFileName)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Document not found"));
        return new StoredDocument(document.getFileName(), document.getContentType(), document.getSizeBytes(), document.getContent());
    }

    private String getAllowedExtension(String originalFileName, String contentType) {
        String extension = extractExtension(originalFileName);
        if (ALLOWED_EXTENSIONS.contains(extension)) {
            return extension;
        }

        return switch (contentType.toLowerCase(Locale.ROOT)) {
            case "image/jpeg", "image/jpg", "image/pjpeg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            case "image/heic", "image/heif", "image/heic-sequence", "image/heif-sequence" -> ".heic";
            default -> throw new ApiException(HttpStatus.BAD_REQUEST, "Unsupported image type: " + contentType);
        };
    }

    private String getStoredContentType(String contentType, String extension) {
        return switch (contentType.toLowerCase(Locale.ROOT)) {
            case "image/jpeg", "image/jpg", "image/pjpeg" -> "image/jpeg";
            case "image/png" -> "image/png";
            case "image/webp" -> "image/webp";
            case "image/heic", "image/heif", "image/heic-sequence", "image/heif-sequence" -> "image/heic";
            default -> switch (extension) {
                case ".jpg", ".jpeg" -> "image/jpeg";
                case ".png" -> "image/png";
                case ".webp" -> "image/webp";
                case ".heic" -> "image/heic";
                default -> throw new ApiException(HttpStatus.BAD_REQUEST, "Unsupported image type");
            };
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

    private String sanitizeOriginalFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return null;
        }
        return Path.of(fileName).getFileName().toString();
    }

    public record DocumentUploadResponse(String path, String fileName, String contentType, long size) {
    }

    public record StoredDocument(String fileName, String contentType, long size, byte[] content) {
    }
}
