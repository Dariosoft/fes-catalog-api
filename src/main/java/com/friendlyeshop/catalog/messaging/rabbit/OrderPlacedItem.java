package com.friendlyeshop.catalog.messaging.rabbit;

import java.util.UUID;

public record OrderPlacedItem(UUID productId, Integer quantity) {
}
