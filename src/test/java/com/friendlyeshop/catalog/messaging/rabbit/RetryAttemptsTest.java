package com.friendlyeshop.catalog.messaging.rabbit;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

class RetryAttemptsTest {

    @Test
    void firstDeliveryCountsAsOneAttempt() {
        assertThat(RetryAttempts.of(messageWithoutHeader())).isEqualTo(1);
    }

    @Test
    void countsDeathsFromTheRetryQueue() {
        Message message = messageWithXDeath(List.of(
                Map.of("queue", RetryAttempts.RETRY_QUEUE, "count", 2L),
                Map.of("queue", RetryAttempts.RETRY_QUEUE, "count", 1L)));

        assertThat(RetryAttempts.of(message)).isEqualTo(4);
    }

    @Test
    void ignoresDeathsFromOtherQueues() {
        Message message = messageWithXDeath(List.of(
                Map.of("queue", "stock.reserve", "count", 5L),
                Map.of("queue", RetryAttempts.RETRY_QUEUE, "count", 1L)));

        assertThat(RetryAttempts.of(message)).isEqualTo(2);
    }

    @Test
    void treatsMissingCountAsOneDeath() {
        Message message = messageWithXDeath(List.of(Map.of("queue", RetryAttempts.RETRY_QUEUE)));

        assertThat(RetryAttempts.of(message)).isEqualTo(2);
    }

    private static Message messageWithoutHeader() {
        return new Message("{}".getBytes(StandardCharsets.UTF_8), new MessageProperties());
    }

    private static Message messageWithXDeath(List<Map<String, ?>> xDeath) {
        MessageProperties properties = new MessageProperties();
        properties.setHeader("x-death", xDeath);
        return new Message("{}".getBytes(StandardCharsets.UTF_8), properties);
    }
}
