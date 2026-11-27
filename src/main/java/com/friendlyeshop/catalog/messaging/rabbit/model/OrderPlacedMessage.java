package com.friendlyeshop.catalog.messaging.rabbit.model;

import java.util.List;
import java.util.UUID;

public record OrderPlacedMessage(UUID eventId, UUID orderId, List<OrderPlacedItem> items) {
}
