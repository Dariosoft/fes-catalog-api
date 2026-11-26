package com.friendlyeshop.catalog.messaging.rabbit;

import java.util.List;
import java.util.UUID;

public record OrderPlacedMessage(UUID eventId, UUID orderId, List<OrderPlacedItem> items) {
}
