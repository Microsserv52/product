package Microsserv52.product.service.image;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "product.image")
public class ImageStorageProperties {
    public enum Mode { MOCK, S3 }

    public static class S3Settings {
        private String bucket = "";
        private String region = "";
        private String mediaOrigin = "";

        public String getBucket() {
            return bucket;
        }

        public void setBucket(String bucket) {
            this.bucket = bucket;
        }

        public String getRegion() {
            return region;
        }

        public void setRegion(String region) {
            this.region = region;
        }

        public String getMediaOrigin() {
            return mediaOrigin;
        }

        public void setMediaOrigin(String mediaOrigin) {
            this.mediaOrigin = mediaOrigin;
        }
    }

    private Mode mode = Mode.MOCK;
    private String storageUrl = "http://image-storage:8090";
    private String mediaBaseUrl = "/media";
    private Duration uploadExpiry = Duration.ofMinutes(10);
    private final S3Settings s3 = new S3Settings();

    public Mode getMode() {
        return mode;
    }

    public void setMode(Mode mode) {
        this.mode = mode;
    }

    public String getStorageUrl() {
        return storageUrl;
    }

    public void setStorageUrl(String storageUrl) {
        this.storageUrl = storageUrl;
    }

    public String getMediaBaseUrl() {
        return mediaBaseUrl;
    }

    public void setMediaBaseUrl(String mediaBaseUrl) {
        this.mediaBaseUrl = mediaBaseUrl;
    }

    public Duration getUploadExpiry() {
        return uploadExpiry;
    }

    public void setUploadExpiry(Duration uploadExpiry) {
        this.uploadExpiry = uploadExpiry;
    }

    public S3Settings getS3() {
        return s3;
    }
}
