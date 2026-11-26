package com.friendlyeshop.catalog.model;

import com.friendlyeshop.catalog.model.enums.RejectionReason;
import com.friendlyeshop.catalog.model.enums.ReservationStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.Getter;
import org.springframework.data.domain.Persistable;

@Entity
@Table(name = "stock_reservations")
@Getter
public class StockReservation implements Persistable<UUID> {

    @Id
    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Transient
    private boolean fresh = true;

    @Column(name = "event_id")
    private UUID eventId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ReservationStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason", length = 30)
    private RejectionReason reason;

    @Column(name = "reserved_at")
    private Instant reservedAt;

    @Column(name = "rejected_at")
    private Instant rejectedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected StockReservation() {
    }

    public static StockReservation reserved(UUID eventId, UUID orderId, Instant now) {
        StockReservation reservation = newResult(eventId, orderId, now);
        reservation.status = ReservationStatus.RESERVED;
        reservation.reservedAt = now;
        return reservation;
    }

    public static StockReservation rejected(UUID eventId, UUID orderId, RejectionReason reason, Instant now) {
        StockReservation reservation = newResult(eventId, orderId, now);
        reservation.status = ReservationStatus.REJECTED;
        reservation.reason = Objects.requireNonNull(reason, "reason");
        reservation.rejectedAt = now;
        return reservation;
    }

    private static StockReservation newResult(UUID eventId, UUID orderId, Instant now) {
        StockReservation reservation = new StockReservation();
        reservation.orderId = Objects.requireNonNull(orderId, "orderId");
        reservation.eventId = eventId;
        reservation.createdAt = Objects.requireNonNull(now, "now");
        reservation.updatedAt = now;
        return reservation;
    }

    @Override
    public UUID getId() {
        return orderId;
    }

    @Override
    public boolean isNew() {
        return fresh;
    }

    @PostLoad
    @PostPersist
    void markPersisted() {
        this.fresh = false;
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
