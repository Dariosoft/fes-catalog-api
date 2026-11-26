package com.friendlyeshop.catalog.messaging.rabbit.reader;

import static org.assertj.core.api.Assertions.assertThat;

import com.friendlyeshop.catalog.messaging.rabbit.model.OrderPlacedItem;
import com.friendlyeshop.catalog.messaging.rabbit.model.OrderPlacedMessage;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class OrderPlacedMessageReaderTest {

    private static final UUID ORDER_ID = UUID.fromString("1a2b3c4d-0000-4000-8000-000000000001");

    private static final UUID EVENT_ID = UUID.fromString("8f3c2b1a-0000-4000-8000-000000000002");

    private static final UUID PRODUCT_ID = UUID.fromString("3c4d5e6f-0000-4000-8000-000000000003");

    private OrderPlacedMessageReader reader;

    @BeforeEach
    void setUp() {
        reader = new OrderPlacedMessageReader(new JsonMapper());
    }

    @Test
    void readsMinimalContractAndIgnoresExtraFields() {
        String body = """
                {
                  "eventId": "%s",
                  "eventType": "order.placed",
                  "occurredAt": "2026-11-25T12:00:00Z",
                  "orderId": "%s",
                  "tenantId": "%s",
                  "items": [
                    {"productId": "%s", "quantity": 2, "unitPrice": 10.00, "currency": "ARS"}
                  ],
                  "total": 20.00,
                  "currency": "ARS"
                }
                """.formatted(EVENT_ID, ORDER_ID, UUID.randomUUID(), PRODUCT_ID);

        Optional<OrderPlacedMessage> message = reader.read(bytes(body));

        assertThat(message).isPresent();
        assertThat(message.get().eventId()).isEqualTo(EVENT_ID);
        assertThat(message.get().orderId()).isEqualTo(ORDER_ID);
        assertThat(message.get().items())
                .containsExactly(new OrderPlacedItem(PRODUCT_ID, 2));
    }

    @Test
    void returnsEmptyWithoutUsableOrderId() {
        assertThat(reader.read(bytes("{}"))).isEmpty();
        assertThat(reader.read(bytes("{\"orderId\": \"\"}"))).isEmpty();
        assertThat(reader.read(bytes("{\"orderId\": \"not-a-uuid\"}"))).isEmpty();
        assertThat(reader.read(bytes("{\"orderId\": 42}"))).isEmpty();
        assertThat(reader.read(bytes("not-json"))).isEmpty();
        assertThat(reader.read(bytes(""))).isEmpty();
    }

    @Test
    void keepsMissingEventIdAsNull() {
        Optional<OrderPlacedMessage> message = reader.read(bytes("{\"orderId\": \"%s\"}".formatted(ORDER_ID)));

        assertThat(message).isPresent();
        assertThat(message.get().eventId()).isNull();
    }

    @Test
    void treatsMissingOrNonListItemsAsEmpty() {
        assertThat(reader.read(bytes("{\"orderId\": \"%s\"}".formatted(ORDER_ID))).orElseThrow().items()).isEmpty();
        assertThat(reader.read(bytes("{\"orderId\": \"%s\", \"items\": {}}".formatted(ORDER_ID)))
                .orElseThrow().items()).isEmpty();
        assertThat(reader.read(bytes("{\"orderId\": \"%s\", \"items\": null}".formatted(ORDER_ID)))
                .orElseThrow().items()).isEmpty();
    }

    @Test
    void marksInvalidItemFieldsAsNullInsteadOfFailingTheParse() {
        String body = """
                {
                  "orderId": "%s",
                  "items": [
                    {"productId": "not-a-uuid", "quantity": 2},
                    {"productId": "%s", "quantity": "two"},
                    {"productId": "%s", "quantity": 2.5},
                    {"productId": "%s", "quantity": 99999999999999},
                    "unexpected"
                  ]
                }
                """.formatted(ORDER_ID, PRODUCT_ID, PRODUCT_ID, PRODUCT_ID);

        OrderPlacedMessage message = reader.read(bytes(body)).orElseThrow();

        assertThat(message.items()).containsExactly(
                new OrderPlacedItem(null, 2),
                new OrderPlacedItem(PRODUCT_ID, null),
                new OrderPlacedItem(PRODUCT_ID, null),
                new OrderPlacedItem(PRODUCT_ID, null),
                new OrderPlacedItem(null, null));
    }

    @Test
    void preservesRepeatedItemsForTheServiceToAggregate() {
        String body = """
                {
                  "orderId": "%s",
                  "items": [
                    {"productId": "%s", "quantity": 2},
                    {"productId": "%s", "quantity": 3}
                  ]
                }
                """.formatted(ORDER_ID, PRODUCT_ID, PRODUCT_ID);

        OrderPlacedMessage message = reader.read(bytes(body)).orElseThrow();

        assertThat(message.items()).containsExactly(
                new OrderPlacedItem(PRODUCT_ID, 2),
                new OrderPlacedItem(PRODUCT_ID, 3));
    }

    private static byte[] bytes(String content) {
        return content.getBytes(StandardCharsets.UTF_8);
    }
}
