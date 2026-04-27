package mr.btp.api.document;

import mr.btp.api.common.exception.ApiException;
import mr.btp.api.common.i18n.MessageKey;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class DocumentStorageService {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(".jpg", ".jpeg", ".png", ".webp", ".heic");

    private final UploadedDocumentRepository uploadedDocumentRepository;
    private final DocumentUploadClient documentUploadClient;

    public DocumentStorageService(UploadedDocumentRepository uploadedDocumentRepository,
                                  DocumentUploadClient documentUploadClient) {
        this.uploadedDocumentRepository = uploadedDocumentRepository;
        this.documentUploadClient = documentUploadClient;
    }

    public DocumentUploadResponse storeImage(MultipartFile file) {
        if (file.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "error.document.upload.empty", "Uploaded file is empty");
        }

        String contentType = file.getContentType();
        if (contentType == null || !contentType.toLowerCase(Locale.ROOT).startsWith("image/")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "error.document.image-only", "Only image uploads are supported");
        }

        String extension = getAllowedExtension(file.getOriginalFilename(), contentType);
        String storedContentType = getStoredContentType(contentType, extension);
        String originalFileName = sanitizeOriginalFileName(file.getOriginalFilename());

        try {
            CloudinaryStorageClient.CloudinaryUpload uploaded = documentUploadClient.uploadImage(file.getBytes(), originalFileName, storedContentType);
            return new DocumentUploadResponse(uploaded.secureUrl(), uploaded.publicId(), storedContentType, uploaded.size() > 0 ? uploaded.size() : file.getSize(), uploaded.publicId(), originalFileName);
        } catch (IOException exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "error.document.store-failed", "Could not store uploaded image");
        }
    }

    public List<DocumentUploadResponse> storeImages(List<MultipartFile> files) {
        List<DocumentUploadResponse> uploadedDocuments = new ArrayList<>();
        try {
            for (MultipartFile file : files == null ? List.<MultipartFile>of() : files) {
                if (file != null && !file.isEmpty()) {
                    uploadedDocuments.add(storeImage(file));
                }
            }
            return uploadedDocuments;
        } catch (RuntimeException exception) {
            deleteImagesQuietly(uploadedDocuments.stream()
                    .map(DocumentUploadResponse::publicId)
                    .toList());
            throw exception;
        }
    }

    public void deleteImage(String publicId) {
        if (publicId == null || publicId.isBlank()) {
            return;
        }
        documentUploadClient.deleteImage(publicId);
    }

    public void deleteImagesQuietly(Collection<String> publicIds) {
        for (String publicId : publicIds == null ? List.<String>of() : publicIds) {
            try {
                deleteImage(publicId);
            } catch (RuntimeException ignored) {
            }
        }
    }

    public StoredDocument load(String fileName) {
        String safeFileName = sanitizeFileName(fileName);
        UploadedDocument document = uploadedDocumentRepository.findByFileName(safeFileName)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "error.resource.not-found",
                        "{0} not found",
                        MessageKey.of("resource.document", "Document")
                ));
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
            default -> throw new ApiException(HttpStatus.BAD_REQUEST, "error.document.unsupported-image-type.with-content-type", "Unsupported image type: {0}", contentType);
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
                default -> throw new ApiException(HttpStatus.BAD_REQUEST, "error.document.unsupported-image-type", "Unsupported image type");
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
            throw new ApiException(HttpStatus.BAD_REQUEST, "error.document.invalid-path", "Invalid document path");
        }
        return safeFileName;
    }

    private String sanitizeOriginalFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return null;
        }
        return Path.of(fileName).getFileName().toString();
    }

    public record DocumentUploadResponse(String path, String fileName, String contentType, long size, String publicId, String originalFileName) {
    }

    public record StoredDocument(String fileName, String contentType, long size, byte[] content) {
    }
}
