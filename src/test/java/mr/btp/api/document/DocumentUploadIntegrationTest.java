package mr.btp.api.document;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.mock.web.MockMultipartFile;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
@Testcontainers
class DocumentUploadIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private DocumentStorageService documentStorageService;

    @Autowired
    private UploadedDocumentRepository uploadedDocumentRepository;

    @MockBean
    private DocumentUploadClient documentUploadClient;

    @Test
    void shouldUploadImageToCloudinary() {
        byte[] content = new byte[]{10, 20, 30, 40, 50};
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test-image.jpg",
                "image/jpeg",
                content
        );
        when(documentUploadClient.uploadImage(any(byte[].class), eq("test-image.jpg"), eq("image/jpeg")))
                .thenReturn(new CloudinaryStorageClient.CloudinaryUpload(
                        "https://res.cloudinary.com/demo/image/upload/v1/btp/documents/test-image.jpg",
                        "btp/documents/test-image",
                        "image",
                        "jpg",
                        content.length
                ));

        DocumentStorageService.DocumentUploadResponse uploadResponse = documentStorageService.storeImage(file);

        assertThat(uploadResponse.path()).startsWith("https://res.cloudinary.com/");
        assertThat(uploadResponse.fileName()).isEqualTo("btp/documents/test-image");
        assertThat(uploadResponse.publicId()).isEqualTo("btp/documents/test-image");
        assertThat(uploadResponse.contentType()).isEqualTo("image/jpeg");
        assertThat(uploadResponse.size()).isEqualTo(content.length);
        assertThat(uploadedDocumentRepository.findByFileName("btp/documents/test-image")).isEmpty();
        verify(documentUploadClient).uploadImage(any(byte[].class), eq("test-image.jpg"), eq("image/jpeg"));
    }

    @Test
    void shouldLoadLegacyImageFromPostgresBytea() {
        byte[] content = new byte[]{10, 20, 30, 40, 50};
        UploadedDocument document = new UploadedDocument();
        document.setFileName("legacy-image.jpg");
        document.setOriginalFileName("legacy-image.jpg");
        document.setContentType("image/jpeg");
        document.setSizeBytes(content.length);
        document.setContent(content);
        uploadedDocumentRepository.save(document);

        DocumentStorageService.StoredDocument storedDocument = documentStorageService.load("legacy-image.jpg");
        assertThat(storedDocument.content()).containsExactly(content);
        assertThat(storedDocument.contentType()).isEqualTo("image/jpeg");
        assertThat(storedDocument.size()).isEqualTo(content.length);
    }

    @Test
    void shouldHandleMobileSpecificImageTypesBeforeCloudinaryUpload() {
        String[] mobileTypes = {"image/jpg", "image/heic-sequence"};
        for (String type : mobileTypes) {
            MockMultipartFile file = new MockMultipartFile(
                    "file",
                    "mobile-photo",
                    type,
                    new byte[]{1, 2, 3}
            );
            when(documentUploadClient.uploadImage(any(byte[].class), eq("mobile-photo"), any()))
                    .thenReturn(new CloudinaryStorageClient.CloudinaryUpload(
                            "https://res.cloudinary.com/demo/image/upload/v1/btp/documents/mobile-photo",
                            "btp/documents/mobile-photo",
                            "image",
                            "jpg",
                            3
                    ));

            DocumentStorageService.DocumentUploadResponse response = documentStorageService.storeImage(file);
            assertThat(response.path()).startsWith("https://res.cloudinary.com/");
            assertThat(response.contentType()).isIn("image/jpeg", "image/heic");
        }
    }
}
