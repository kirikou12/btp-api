package mr.btp.api.document;

public interface DocumentUploadClient {

    CloudinaryStorageClient.CloudinaryUpload uploadImage(byte[] content, String originalFileName, String contentType);

    void deleteImage(String publicId);
}
