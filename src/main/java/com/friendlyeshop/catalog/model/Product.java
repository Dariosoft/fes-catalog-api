package com.friendlyeshop.catalog.model;

import com.friendlyeshop.catalog.exception.InvalidProductException;
import com.friendlyeshop.catalog.model.converters.ProductStageConverter;
import com.friendlyeshop.catalog.model.enums.Currency;
import com.friendlyeshop.catalog.model.enums.ProductStage;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.Getter;

@Entity
@Table(name = "products")
@Getter
public class Product {

    @Id
    private UUID id;

    @Column(name = "owner_account_id")
    private UUID ownerAccountId;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "price", nullable = false, precision = 19, scale = 2)
    private BigDecimal price;

    @Enumerated(EnumType.STRING)
    @Column(name = "currency", nullable = false, length = 3)
    private Currency currency;

    @Column(name = "stock")
    private Integer stock;

    @Convert(converter = ProductStageConverter.class)
    @Column(name = "stage", nullable = false, length = 20)
    private ProductStage stage;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Product() {
    }

    public static Product openNew(UUID ownerAccountId, String name, BigDecimal price, Currency currency,
            Integer stock) {
        Product product = new Product();
        product.id = UUID.randomUUID();
        product.ownerAccountId = ownerAccountId;
        product.name = name;
        product.price = price;
        product.currency = currency;
        product.stock = stock;
        product.stage = ProductStage.DRAFT;
        return product;
    }

    public void update(String name, BigDecimal price, Currency currency, Integer stock) {
        this.name = name;
        this.price = price;
        this.currency = currency;
        this.stock = stock;
    }

    public void publish() {
        if (ownerAccountId == null) {
            throw new InvalidProductException("El producto debe tener un dueño para publicarse");
        }
        if (name == null || name.isBlank()) {
            throw new InvalidProductException("El producto debe tener un nombre para publicarse");
        }
        if (price == null) {
            throw new InvalidProductException("El producto debe tener un precio para publicarse");
        }
        this.stage = ProductStage.PUBLISHED;
    }

    public void unpublish() {
        this.stage = ProductStage.DRAFT;
    }

    public void takeOwnership(UUID accountId) {
        this.ownerAccountId = accountId;
    }

    public void deleteLogically(Instant now) {
        this.deletedAt = now;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public boolean belongsTo(UUID accountId) {
        return Objects.equals(ownerAccountId, accountId);
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
