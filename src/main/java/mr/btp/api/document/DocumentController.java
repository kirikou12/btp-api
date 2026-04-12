package mr.btp.api.document;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/uploads")
public class DocumentController {

    private final DocumentStorageService documentStorageService;

    public DocumentController(DocumentStorageService documentStorageService) {
        this.documentStorageService = documentStorageService;
    }

    @PostMapping("/images")
    public DocumentStorageService.DocumentUploadResponse uploadImage(@RequestParam("file") MultipartFile file) {
        return documentStorageService.storeImage(file);
    }

    @GetMapping("/{fileName}")
    public ResponseEntity<byte[]> getFile(@PathVariable String fileName) {
        DocumentStorageService.StoredDocument document = documentStorageService.load(fileName);
        MediaType mediaType = document.contentType() != null ? MediaType.parseMediaType(document.contentType()) : MediaType.APPLICATION_OCTET_STREAM;

        return ResponseEntity.ok()
                .contentType(mediaType)
                .contentLength(document.size())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().filename(document.fileName()).build().toString())
                .body(document.content());
    }
}
