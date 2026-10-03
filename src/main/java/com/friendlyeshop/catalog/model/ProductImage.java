package com.friendlyeshop.catalog.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

@Entity
@Table(name = "product_images")
@Getter
public class ProductImage {

    @Id
    private UUID id;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "object_key", nullable = false, length = 512)
    private String objectKey;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ProductImage() {
    }

    public static ProductImage of(UUID id, UUID productId, String objectKey, String contentType) {
        ProductImage image = new ProductImage();
        image.id = id;
        image.productId = productId;
        image.objectKey = objectKey;
        image.contentType = contentType;
        return image;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }
}
