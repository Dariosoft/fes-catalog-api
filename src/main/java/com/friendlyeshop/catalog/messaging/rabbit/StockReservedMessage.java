package com.friendlyeshop.catalog.messaging.rabbit;

import com.friendlyeshop.catalog.model.StockReservation;
import java.time.Instant;
import java.util.UUID;

public record StockReservedMessage(UUID eventId, UUID orderId, Instant reservedAt) {

    public static StockReservedMessage from(StockReservation reservation) {
        return new StockReservedMessage(
                reservation.getEventId(), reservation.getOrderId(), reservation.getReservedAt());
    }
}
