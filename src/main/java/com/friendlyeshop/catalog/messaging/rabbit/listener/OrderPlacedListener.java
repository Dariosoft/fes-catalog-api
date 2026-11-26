package com.friendlyeshop.catalog.messaging.rabbit.listener;

import com.friendlyeshop.catalog.messaging.rabbit.RetryAttempts;
import com.friendlyeshop.catalog.messaging.rabbit.model.OrderPlacedMessage;
import com.friendlyeshop.catalog.messaging.rabbit.publisher.StockReserveDeadLetterPublisher;
import com.friendlyeshop.catalog.messaging.rabbit.publisher.StockResultPublisher;
import com.friendlyeshop.catalog.messaging.rabbit.reader.OrderPlacedMessageReader;
import com.friendlyeshop.catalog.model.StockReservation;
import com.friendlyeshop.catalog.service.StockReservationService;
import com.rabbitmq.client.Channel;
import java.io.IOException;
import java.util.Optional;
import java.util.UUID;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

@Component
public class OrderPlacedListener {

    private static final int MAX_ATTEMPTS = 5;

    private final OrderPlacedMessageReader reader;

    private final StockReservationService reservationService;

    private final StockResultPublisher resultPublisher;

    private final StockReserveDeadLetterPublisher deadLetterPublisher;

    public OrderPlacedListener(OrderPlacedMessageReader reader, StockReservationService reservationService,
            StockResultPublisher resultPublisher, StockReserveDeadLetterPublisher deadLetterPublisher) {
        this.reader = reader;
        this.reservationService = reservationService;
        this.resultPublisher = resultPublisher;
        this.deadLetterPublisher = deadLetterPublisher;
    }

    @RabbitListener(queues = "stock.reserve")
    public void onOrderPlaced(Message message, Channel channel) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        Optional<OrderPlacedMessage> parsed = reader.read(message.getBody());
        if (parsed.isEmpty()) {
            rejectOrDeadLetter(channel, deliveryTag, message);
            return;
        }
        process(parsed.get(), message, channel, deliveryTag);
    }

    private void process(OrderPlacedMessage orderPlaced, Message message, Channel channel, long deliveryTag)
            throws IOException {
        try {
            StockReservation reservation = reservationService.reserve(orderPlaced);
            resultPublisher.publish(reservation);
        } catch (DataIntegrityViolationException conflict) {
            replayStoredResult(orderPlaced.orderId(), message, channel, deliveryTag);
            return;
        } catch (RuntimeException failure) {
            rejectOrDeadLetter(channel, deliveryTag, message);
            return;
        }
        channel.basicAck(deliveryTag, false);
    }

    private void replayStoredResult(UUID orderId, Message message, Channel channel, long deliveryTag)
            throws IOException {
        Optional<StockReservation> stored = reservationService.find(orderId);
        if (stored.isEmpty()) {
            rejectOrDeadLetter(channel, deliveryTag, message);
            return;
        }
        try {
            resultPublisher.publish(stored.get());
        } catch (RuntimeException failure) {
            rejectOrDeadLetter(channel, deliveryTag, message);
            return;
        }
        channel.basicAck(deliveryTag, false);
    }

    private void rejectOrDeadLetter(Channel channel, long deliveryTag, Message message) throws IOException {
        if (RetryAttempts.of(message) >= MAX_ATTEMPTS) {
            deadLetterPublisher.publish(message);
            channel.basicAck(deliveryTag, false);
        } else {
            channel.basicNack(deliveryTag, false, false);
        }
    }
}
