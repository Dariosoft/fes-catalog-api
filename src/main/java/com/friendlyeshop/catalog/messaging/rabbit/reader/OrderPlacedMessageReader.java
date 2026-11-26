package com.friendlyeshop.catalog.messaging.rabbit.reader;

import com.friendlyeshop.catalog.messaging.rabbit.model.OrderPlacedItem;
import com.friendlyeshop.catalog.messaging.rabbit.model.OrderPlacedMessage;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Component
public class OrderPlacedMessageReader {

    private final JsonMapper jsonMapper;

    public OrderPlacedMessageReader(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    public Optional<OrderPlacedMessage> read(byte[] body) {
        Optional<JsonNode> parsed = parse(body);
        if (parsed.isEmpty()) {
            return Optional.empty();
        }
        JsonNode root = parsed.get();
        UUID orderId = readUuid(root.get("orderId"));
        if (orderId == null) {
            return Optional.empty();
        }
        UUID eventId = readUuid(root.get("eventId"));
        return Optional.of(new OrderPlacedMessage(eventId, orderId, readItems(root.get("items"))));
    }

    private Optional<JsonNode> parse(byte[] body) {
        try {
            JsonNode root = jsonMapper.readTree(body);
            return root != null && root.isObject() ? Optional.of(root) : Optional.empty();
        } catch (JacksonException invalidJson) {
            return Optional.empty();
        }
    }

    private List<OrderPlacedItem> readItems(JsonNode itemsNode) {
        if (itemsNode == null || !itemsNode.isArray()) {
            return List.of();
        }
        List<OrderPlacedItem> items = new ArrayList<>();
        for (JsonNode itemNode : itemsNode) {
            items.add(readItem(itemNode));
        }
        return items;
    }

    private OrderPlacedItem readItem(JsonNode itemNode) {
        if (itemNode == null || !itemNode.isObject()) {
            return new OrderPlacedItem(null, null);
        }
        return new OrderPlacedItem(readUuid(itemNode.get("productId")), readQuantity(itemNode.get("quantity")));
    }

    private Integer readQuantity(JsonNode quantityNode) {
        if (quantityNode == null || !quantityNode.isIntegralNumber() || !quantityNode.canConvertToInt()) {
            return null;
        }
        return quantityNode.intValue();
    }

    private UUID readUuid(JsonNode uuidNode) {
        if (uuidNode == null || !uuidNode.isTextual()) {
            return null;
        }
        String value = uuidNode.textValue().strip();
        if (value.isEmpty()) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException invalidUuid) {
            return null;
        }
    }
}
