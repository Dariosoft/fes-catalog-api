package com.friendlyeshop.catalog.messaging.rabbit;

import static org.mockito.Mockito.verify;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

@ExtendWith(MockitoExtension.class)
class StockReserveDeadLetterPublisherTest {

    @Mock
    private RabbitTemplate rabbitTemplate;

    @Test
    void sendsTheOriginalMessageToTheDeadLetterQueue() {
        StockReserveDeadLetterPublisher publisher = new StockReserveDeadLetterPublisher(rabbitTemplate);
        Message message = new Message("{}".getBytes(StandardCharsets.UTF_8), new MessageProperties());

        publisher.publish(message);

        verify(rabbitTemplate).send("", StockReserveDeadLetterPublisher.DEAD_LETTER_QUEUE, message);
    }
}
