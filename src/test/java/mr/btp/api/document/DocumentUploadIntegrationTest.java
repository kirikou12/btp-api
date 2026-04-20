package mr.btp.api.document;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.mock.web.MockMultipartFile;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
class DocumentUploadIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private DocumentStorageService documentStorageService;

    @Test
    void shouldStoreAndLoadImageUsingPostgresBytea() {
        byte[] content = new byte[]{10, 20, 30, 40, 50};
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test-image.jpg",
                "image/jpeg",
                content
        );

        // Test Upload
        DocumentStorageService.DocumentUploadResponse uploadResponse = documentStorageService.storeImage(file);
        assertThat(uploadResponse.fileName()).isNotNull();
        assertThat(uploadResponse.contentType()).isEqualTo("image/jpeg");

        // Test Load (Verify bytea storage in Postgres)
        DocumentStorageService.StoredDocument storedDocument = documentStorageService.load(uploadResponse.fileName());
        assertThat(storedDocument.content()).containsExactly(content);
        assertThat(storedDocument.contentType()).isEqualTo("image/jpeg");
        assertThat(storedDocument.size()).isEqualTo(content.length);
    }

    @Test
    void shouldHandleMobileSpecificImageTypesInPostgres() {
        String[] mobileTypes = {"image/jpg", "image/heic-sequence"};
        for (String type : mobileTypes) {
            MockMultipartFile file = new MockMultipartFile(
                    "file",
                    "mobile-photo",
                    type,
                    new byte[]{1, 2, 3}
            );

            DocumentStorageService.DocumentUploadResponse response = documentStorageService.storeImage(file);
            assertThat(response.path()).startsWith("/api/uploads/");
            
            DocumentStorageService.StoredDocument loaded = documentStorageService.load(response.fileName());
            assertThat(loaded.content()).hasSize(3);
        }
    }
}
