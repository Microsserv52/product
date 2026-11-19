package Microsserv52.product.service.image;

import java.util.Map;

public interface ImageStorage {
    PresignedPut presignPut(String key, String contentType, long size);

    byte[] fetch(String key);

    void saveMedia(String key, byte[] bytes, String contentType);

    void delete(String key);

    String displayUrl(String key);

    record PresignedPut(String url, Map<String, String> headers) {
    }
}
