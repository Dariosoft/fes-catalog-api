package com.friendlyeshop.catalog.messaging.rabbit.model;

import java.util.UUID;

public record OrderPlacedItem(UUID productId, Integer quantity) {
}
