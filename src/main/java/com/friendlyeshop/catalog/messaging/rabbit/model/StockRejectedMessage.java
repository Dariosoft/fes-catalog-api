package com.friendlyeshop.catalog.messaging.rabbit.model;

import com.friendlyeshop.catalog.model.StockReservation;
import com.friendlyeshop.catalog.model.enums.RejectionReason;
import java.time.Instant;
import java.util.UUID;

public record StockRejectedMessage(UUID eventId, UUID orderId, RejectionReason reason, Instant rejectedAt) {

    public static StockRejectedMessage from(StockReservation reservation) {
        return new StockRejectedMessage(reservation.getEventId(), reservation.getOrderId(),
                reservation.getReason(), reservation.getRejectedAt());
    }
}
