package mr.btp.api.document;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UploadedDocumentRepository extends JpaRepository<UploadedDocument, Long> {

    Optional<UploadedDocument> findByFileName(String fileName);
}
