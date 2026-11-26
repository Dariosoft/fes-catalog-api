package com.friendlyeshop.catalog.messaging.rabbit.publisher;

import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
public class StockReserveDeadLetterPublisher {

    static final String DEAD_LETTER_QUEUE = "stock.reserve.dlq";

    private final RabbitTemplate rabbitTemplate;

    public StockReserveDeadLetterPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publish(Message message) {
        rabbitTemplate.send("", DEAD_LETTER_QUEUE, message);
    }
}
