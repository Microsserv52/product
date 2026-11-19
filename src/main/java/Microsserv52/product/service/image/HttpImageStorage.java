package Microsserv52.product.service.image;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.function.Function;

import Microsserv52.product.service.DependencyUnavailableException;
import Microsserv52.product.service.InvalidRequestException;

/**
 * Stores objects on a local S3-compatible blob mock (see mocks/image-storage).
 * Objects live under {@code <baseUrl>/object/<key>}; direct uploads land at
 * {@code <baseUrl>/upload/<key>}; public reads are served from
 * {@code <baseUrl>/media/<key>} through the platform edge.
 */
public class HttpImageStorage implements ImageStorage {
    private final String baseUrl;
    private final String mediaBaseUrl;
    private final HttpClient client;

    public HttpImageStorage(String baseUrl, String mediaBaseUrl) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.mediaBaseUrl = mediaBaseUrl.endsWith("/") ? mediaBaseUrl.substring(0, mediaBaseUrl.length() - 1) : mediaBaseUrl;
        this.client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    }

    @Override
    public PresignedPut presignPut(String key, String contentType, long size) {
        return new PresignedPut(baseUrl + "/upload/" + key, Map.of("If-None-Match", "*"));
    }

    @Override
    public byte[] fetch(String key) {
        HttpResponse<byte[]> response = exchange(baseUrl + "/object/" + key,
            builder -> builder.GET().build());
        if (response.statusCode() == 404) {
            throw new InvalidRequestException("image upload is not ready or was already consumed");
        }
        if (response.statusCode() != 200) {
            throw new DependencyUnavailableException(
                "Image storage returned " + response.statusCode() + " when reading " + key);
        }
        return response.body();
    }

    @Override
    public void saveMedia(String key, byte[] bytes, String contentType) {
        HttpResponse<byte[]> response = exchange(baseUrl + "/object/" + key,
            builder -> builder
                .PUT(HttpRequest.BodyPublishers.ofByteArray(bytes))
                .header("Content-Type", contentType)
                .header("If-None-Match", "*")
                .build());
        if (response.statusCode() == 412) {
            throw new ImageStorageConflictException("a validated image already exists for this upload");
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new DependencyUnavailableException(
                "Image storage returned " + response.statusCode() + " when writing " + key);
        }
    }

    @Override
    public void delete(String key) {
        HttpResponse<byte[]> response = exchange(baseUrl + "/object/" + key,
            builder -> builder.DELETE().build());
        if (response.statusCode() != 204 && response.statusCode() != 200 && response.statusCode() != 404) {
            throw new DependencyUnavailableException(
                "Image storage returned " + response.statusCode() + " when deleting " + key);
        }
    }

    @Override
    public String displayUrl(String key) {
        return mediaBaseUrl + "/" + key;
    }

    private HttpResponse<byte[]> exchange(String url,
                                         Function<HttpRequest.Builder, HttpRequest> requestFactory) {
        try {
            HttpRequest request = requestFactory.apply(
                HttpRequest.newBuilder(URI.create(url)));
            return client.send(request, HttpResponse.BodyHandlers.ofByteArray());
        } catch (IOException exception) {
            throw new DependencyUnavailableException("Image storage is unreachable");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new DependencyUnavailableException("Image storage call was interrupted");
        }
    }
}
