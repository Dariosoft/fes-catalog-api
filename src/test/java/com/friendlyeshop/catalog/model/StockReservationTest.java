package com.friendlyeshop.catalog.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.friendlyeshop.catalog.model.enums.RejectionReason;
import com.friendlyeshop.catalog.model.enums.ReservationStatus;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class StockReservationTest {

    private static final UUID ORDER_ID = UUID.fromString("aaaaaaaa-0000-4000-8000-000000000001");

    private static final UUID EVENT_ID = UUID.fromString("bbbbbbbb-0000-4000-8000-000000000002");

    private static final Instant NOW = Instant.parse("2026-11-25T12:00:00Z");

    @Test
    void reservedResultKeepsOnlyItsOwnFields() {
        StockReservation reservation = StockReservation.reserved(EVENT_ID, ORDER_ID, NOW);

        assertThat(reservation.getOrderId()).isEqualTo(ORDER_ID);
        assertThat(reservation.getEventId()).isEqualTo(EVENT_ID);
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.RESERVED);
        assertThat(reservation.getReservedAt()).isEqualTo(NOW);
        assertThat(reservation.getRejectedAt()).isNull();
        assertThat(reservation.getReason()).isNull();
        assertThat(reservation.getId()).isEqualTo(ORDER_ID);
        assertThat(reservation.isNew()).isTrue();
    }

    @Test
    void rejectedResultKeepsItsReasonAndTimestamp() {
        StockReservation reservation = StockReservation.rejected(
                EVENT_ID, ORDER_ID, RejectionReason.PRODUCT_NOT_FOUND, NOW);

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.REJECTED);
        assertThat(reservation.getReason()).isEqualTo(RejectionReason.PRODUCT_NOT_FOUND);
        assertThat(reservation.getRejectedAt()).isEqualTo(NOW);
        assertThat(reservation.getReservedAt()).isNull();
    }

    @Test
    void rejectedResultSupportsEveryContractReason() {
        for (RejectionReason reason : RejectionReason.values()) {
            StockReservation reservation = StockReservation.rejected(UUID.randomUUID(), ORDER_ID, reason, NOW);

            assertThat(reservation.getReason()).isEqualTo(reason);
            assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.REJECTED);
        }
        assertThat(RejectionReason.values()).containsExactly(
                RejectionReason.OUT_OF_STOCK,
                RejectionReason.PRODUCT_NOT_FOUND,
                RejectionReason.PRODUCT_NOT_AVAILABLE,
                RejectionReason.INVALID_QUANTITY,
                RejectionReason.INVALID_ORDER);
    }
}
