package Microsserv52.product.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "product_images")
public class ProductImage {
    @Id
    @Column(name = "product_id")
    private UUID productId;

    @Column(name = "asset_id", nullable = false)
    private UUID assetId;

    @Column(name = "object_key", nullable = false, length = 512)
    private String objectKey;

    @Column(name = "content_type", nullable = false, length = 64)
    private String contentType;

    @Column(nullable = false)
    private long bytes;

    @Column(nullable = false)
    private int width;

    @Column(nullable = false)
    private int height;

    @Column(nullable = false, length = 200)
    private String alt;

    @Column(length = 120)
    private String uploader;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ProductImage() {
    }

    public ProductImage(UUID productId, UUID assetId, String objectKey, String contentType,
                        long bytes, int width, int height, String alt, String uploader) {
        this.productId = productId;
        this.assetId = assetId;
        this.objectKey = objectKey;
        this.contentType = contentType;
        this.bytes = bytes;
        this.width = width;
        this.height = height;
        this.alt = alt;
        this.uploader = uploader;
    }

    @PrePersist
    void prePersist() {
        createdAt = Instant.now();
    }

    public UUID getProductId() {
        return productId;
    }

    public UUID getAssetId() {
        return assetId;
    }

    public String getObjectKey() {
        return objectKey;
    }

    public String getContentType() {
        return contentType;
    }

    public long getBytes() {
        return bytes;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public String getAlt() {
        return alt;
    }

    public void setAlt(String alt) {
        this.alt = alt;
    }

    public String getUploader() {
        return uploader;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
