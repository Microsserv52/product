package Microsserv52.product.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "product_image_uploads")
public class ImageUploadTicket {
    @Id
    private UUID id;

    @Column(name = "product_id", nullable = false)
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

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ImageUploadTicket() {
    }

    public ImageUploadTicket(UUID id, UUID productId, UUID assetId, String objectKey,
                             String contentType, long bytes, int width, int height,
                             Instant expiresAt) {
        this.id = id;
        this.productId = productId;
        this.assetId = assetId;
        this.objectKey = objectKey;
        this.contentType = contentType;
        this.bytes = bytes;
        this.width = width;
        this.height = height;
        this.expiresAt = expiresAt;
    }

    @PrePersist
    void prePersist() {
        createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
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

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getConsumedAt() {
        return consumedAt;
    }

    public void consume() {
        consumedAt = Instant.now();
    }
}
