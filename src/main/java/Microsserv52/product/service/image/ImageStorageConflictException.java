package Microsserv52.product.service.image;

public class ImageStorageConflictException extends RuntimeException {
    public ImageStorageConflictException(String message) {
        super(message);
    }
}
