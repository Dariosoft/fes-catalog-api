package com.friendlyeshop.catalog.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.friendlyeshop.catalog.messaging.rabbit.StockReservedMessage;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.support.converter.MessageConverter;
import tools.jackson.databind.json.JsonMapper;

class RabbitMessagingConfigTest {

    @Test
    void serializesOutgoingRecordsAsJson() {
        MessageConverter converter = new RabbitMessagingConfig().rabbitMessageConverter(new JsonMapper());
        UUID eventId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();

        Message message = converter.toMessage(
                new StockReservedMessage(eventId, orderId, Instant.parse("2026-11-25T12:00:00Z")),
                new MessageProperties());

        String body = new String(message.getBody(), StandardCharsets.UTF_8);
        assertThat(body).contains("\"eventId\":\"" + eventId + "\"");
        assertThat(body).contains("\"orderId\":\"" + orderId + "\"");
        assertThat(body).contains("\"reservedAt\"");
    }
}
