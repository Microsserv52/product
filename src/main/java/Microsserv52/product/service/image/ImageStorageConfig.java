package Microsserv52.product.service.image;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(ImageStorageProperties.class)
public class ImageStorageConfig {

    @Bean
    @ConditionalOnProperty(name = "product.image.mode", havingValue = "mock", matchIfMissing = true)
    ImageStorage mockImageStorage(ImageStorageProperties properties) {
        return new HttpImageStorage(properties.getStorageUrl(), properties.getMediaBaseUrl());
    }

    @Bean
    @ConditionalOnProperty(name = "product.image.mode", havingValue = "s3")
    ImageStorage s3ImageStorage(ImageStorageProperties properties) {
        return new S3ImageStorage(
            properties.getS3().getBucket(),
            properties.getS3().getRegion(),
            properties.getS3().getMediaOrigin(),
            properties.getUploadExpiry());
    }
}
