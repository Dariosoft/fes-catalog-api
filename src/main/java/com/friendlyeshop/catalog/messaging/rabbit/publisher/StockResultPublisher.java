package com.friendlyeshop.catalog.messaging.rabbit.publisher;

import com.friendlyeshop.catalog.exception.StockResultPublishException;
import com.friendlyeshop.catalog.messaging.rabbit.model.StockRejectedMessage;
import com.friendlyeshop.catalog.messaging.rabbit.model.StockReservedMessage;
import com.friendlyeshop.catalog.model.StockReservation;
import com.friendlyeshop.catalog.model.enums.ReservationStatus;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
public class StockResultPublisher {

    static final String STOCK_EXCHANGE = "stock";

    static final String RESERVED_ROUTING_KEY = "stock.reserved";

    static final String REJECTED_ROUTING_KEY = "stock.rejected";

    private static final long CONFIRM_TIMEOUT_MILLIS = 5000L;

    private final RabbitTemplate rabbitTemplate;

    public StockResultPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publish(StockReservation reservation) {
        if (reservation.getStatus() == ReservationStatus.RESERVED) {
            publishConfirmed(RESERVED_ROUTING_KEY, StockReservedMessage.from(reservation));
        } else {
            publishConfirmed(REJECTED_ROUTING_KEY, StockRejectedMessage.from(reservation));
        }
    }

    private void publishConfirmed(String routingKey, Object payload) {
        CorrelationData correlation = new CorrelationData();
        rabbitTemplate.convertAndSend(STOCK_EXCHANGE, routingKey, payload, correlation);
        CorrelationData.Confirm confirm = awaitConfirm(correlation);
        if (!confirm.ack()) {
            throw new StockResultPublishException("Broker rejected the stock result: " + confirm.reason());
        }
    }

    private CorrelationData.Confirm awaitConfirm(CorrelationData correlation) {
        try {
            return correlation.getFuture().get(CONFIRM_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new StockResultPublishException("Interrupted while awaiting the broker confirmation", interrupted);
        } catch (ExecutionException | TimeoutException failure) {
            throw new StockResultPublishException("The broker did not confirm the stock result", failure);
        }
    }
}
