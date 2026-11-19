package Microsserv52.product.service.image;

import java.time.Duration;
import java.util.Map;

import Microsserv52.product.service.DependencyUnavailableException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

/**
 * AWS S3 blob storage. Pending uploads land under private {@code pending/...}
 * keys behind a short-lived presigned PUT; validated display assets are written
 * once to immutable {@code media/...} keys and served read-only through the
 * configured media origin (CloudFront).
 */
public class S3ImageStorage implements ImageStorage {
    private final String bucket;
    private final String mediaOrigin;
    private final Duration signatureDuration;
    private final S3Client s3;
    private final S3Presigner presigner;

    public S3ImageStorage(String bucket, String region, String mediaOrigin, Duration signatureDuration) {
        if (bucket == null || bucket.isBlank()) {
            throw new IllegalStateException("product.image.s3.bucket is required when product.image.mode=s3");
        }
        if (region == null || region.isBlank()) {
            throw new IllegalStateException("product.image.s3.region is required when product.image.mode=s3");
        }
        this.bucket = bucket;
        this.mediaOrigin = mediaOrigin != null ? mediaOrigin.replaceAll("/+$", "") : "https://" + bucket + ".s3." + region + ".amazonaws.com";
        this.signatureDuration = signatureDuration;
        this.s3 = S3Client.builder().region(Region.of(region)).build();
        this.presigner = S3Presigner.builder().region(Region.of(region)).build();
    }

    @Override
    public PresignedPut presignPut(String key, String contentType, long size) {
        PutObjectRequest request = PutObjectRequest.builder()
            .bucket(bucket)
            .key(key)
            .contentType(contentType)
            .contentLength(size)
            .ifNoneMatch("*")
            .build();
        var presigned = presigner.presignPutObject(PutObjectPresignRequest.builder()
            .signatureDuration(signatureDuration)
            .putObjectRequest(request)
            .build());
        return new PresignedPut(presigned.url().toString(), Map.of("If-None-Match", "*"));
    }

    @Override
    public byte[] fetch(String key) {
        try {
            return s3.getObjectAsBytes(GetObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build()).asByteArray();
        } catch (S3Exception exception) {
            if (exception.statusCode() == 404) {
                throw new Microsserv52.product.service.InvalidRequestException(
                    "image upload is not ready or was already consumed");
            }
            throw new DependencyUnavailableException("Image storage read failed: " + exception.awsErrorDetails().errorCode());
        }
    }

    @Override
    public void saveMedia(String key, byte[] bytes, String contentType) {
        try {
            s3.putObject(PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .contentType(contentType)
                    .contentLength((long) bytes.length)
                    .ifNoneMatch("*")
                    .build(),
                RequestBody.fromBytes(bytes));
        } catch (S3Exception exception) {
            if (exception.statusCode() == 412 || exception.awsErrorDetails() != null
                && "PreconditionFailed".equals(exception.awsErrorDetails().errorCode())) {
                throw new ImageStorageConflictException("a validated image already exists for this upload");
            }
            throw new DependencyUnavailableException("Image storage write failed: " + exception.awsErrorDetails().errorCode());
        }
    }

    @Override
    public void delete(String key) {
        try {
            s3.deleteObject(b -> b.bucket(bucket).key(key));
        } catch (S3Exception exception) {
            throw new DependencyUnavailableException("Image storage delete failed: " + exception.awsErrorDetails().errorCode());
        }
    }

    @Override
    public String displayUrl(String key) {
        return mediaOrigin + "/" + key;
    }
}
