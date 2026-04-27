package mr.btp.api.document;

public final class DocumentDtos {

    private DocumentDtos() {
    }

    public record DocumentAttachmentResponse(
            Long id,
            String url,
            String publicId,
            String originalFileName,
            String contentType,
            Long size,
            int sortOrder
    ) {
    }
}
