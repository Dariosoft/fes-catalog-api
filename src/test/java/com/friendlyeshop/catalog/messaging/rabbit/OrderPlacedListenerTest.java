package com.friendlyeshop.catalog.messaging.rabbit;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.friendlyeshop.catalog.model.StockReservation;
import com.friendlyeshop.catalog.model.enums.RejectionReason;
import com.friendlyeshop.catalog.service.StockReservationService;
import com.rabbitmq.client.Channel;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class OrderPlacedListenerTest {

    private static final long DELIVERY_TAG = 7L;

    private static final UUID ORDER_ID = UUID.fromString("aaaaaaaa-0000-4000-8000-000000000001");

    private static final UUID EVENT_ID = UUID.fromString("bbbbbbbb-0000-4000-8000-000000000002");

    private static final UUID PRODUCT_ID = UUID.fromString("cccccccc-0000-4000-8000-000000000003");

    @Mock
    private OrderPlacedMessageReader reader;

    @Mock
    private StockReservationService reservationService;

    @Mock
    private StockResultPublisher resultPublisher;

    @Mock
    private StockReserveDeadLetterPublisher deadLetterPublisher;

    @Mock
    private Channel channel;

    private OrderPlacedListener listener;

    @BeforeEach
    void setUp() {
        listener = new OrderPlacedListener(reader, reservationService, resultPublisher, deadLetterPublisher);
    }

    @Test
    void acknowledgesAfterAReservedAndPublishedResult() throws Exception {
        OrderPlacedMessage orderPlaced = orderPlacedMessage();
        Message message = message(0);
        StockReservation reserved = StockReservation.reserved(EVENT_ID, ORDER_ID, Instant.now());
        when(reader.read(any(byte[].class))).thenReturn(Optional.of(orderPlaced));
        when(reservationService.reserve(orderPlaced)).thenReturn(reserved);

        listener.onOrderPlaced(message, channel);

        verify(resultPublisher).publish(reserved);
        verify(channel).basicAck(DELIVERY_TAG, false);
        verify(channel, never()).basicNack(DELIVERY_TAG, false, false);
        verifyNoInteractions(deadLetterPublisher);
    }

    @Test
    void acknowledgesAfterAReservationRejection() throws Exception {
        OrderPlacedMessage orderPlaced = orderPlacedMessage();
        Message message = message(0);
        StockReservation rejected = StockReservation.rejected(
                EVENT_ID, ORDER_ID, RejectionReason.OUT_OF_STOCK, Instant.now());
        when(reader.read(any(byte[].class))).thenReturn(Optional.of(orderPlaced));
        when(reservationService.reserve(orderPlaced)).thenReturn(rejected);

        listener.onOrderPlaced(message, channel);

        verify(resultPublisher).publish(rejected);
        verify(channel).basicAck(DELIVERY_TAG, false);
    }

    @Test
    void nacksWithoutRequeueingWhenThereIsNoUsableOrderId() throws Exception {
        Message message = message(0);
        when(reader.read(any(byte[].class))).thenReturn(Optional.empty());

        listener.onOrderPlaced(message, channel);

        verify(channel).basicNack(DELIVERY_TAG, false, false);
        verify(channel, never()).basicAck(DELIVERY_TAG, false);
        verifyNoInteractions(reservationService, resultPublisher, deadLetterPublisher);
    }

    @Test
    void retriesWithNackForTheFirstFourAttempts() throws Exception {
        when(reader.read(any(byte[].class))).thenReturn(Optional.empty());

        for (int retries = 0; retries < 4; retries++) {
            listener.onOrderPlaced(message(retries), channel);
        }

        verify(channel, times(4)).basicNack(DELIVERY_TAG, false, false);
        verifyNoInteractions(deadLetterPublisher);
    }

    @Test
    void movesToTheDeadLetterQueueOnTheFifthAttempt() throws Exception {
        Message message = message(4);
        when(reader.read(any(byte[].class))).thenReturn(Optional.empty());

        listener.onOrderPlaced(message, channel);

        verify(deadLetterPublisher).publish(message);
        verify(channel).basicAck(DELIVERY_TAG, false);
        verify(channel, never()).basicNack(DELIVERY_TAG, false, false);
    }

    @Test
    void nacksWhenTheReservationFailsBeforeTheFifthAttempt() throws Exception {
        OrderPlacedMessage orderPlaced = orderPlacedMessage();
        Message message = message(1);
        when(reader.read(any(byte[].class))).thenReturn(Optional.of(orderPlaced));
        when(reservationService.reserve(orderPlaced)).thenThrow(new IllegalStateException("database down"));

        listener.onOrderPlaced(message, channel);

        verify(channel).basicNack(DELIVERY_TAG, false, false);
        verify(channel, never()).basicAck(DELIVERY_TAG, false);
        verifyNoInteractions(resultPublisher);
    }

    @Test
    void deadLettersWhenTheReservationFailsOnTheFifthAttempt() throws Exception {
        OrderPlacedMessage orderPlaced = orderPlacedMessage();
        Message message = message(4);
        when(reader.read(any(byte[].class))).thenReturn(Optional.of(orderPlaced));
        when(reservationService.reserve(orderPlaced)).thenThrow(new IllegalStateException("database down"));

        listener.onOrderPlaced(message, channel);

        verify(deadLetterPublisher).publish(message);
        verify(channel).basicAck(DELIVERY_TAG, false);
        verify(channel, never()).basicNack(DELIVERY_TAG, false, false);
    }

    @Test
    void nacksWhenPublishingTheResultFails() throws Exception {
        OrderPlacedMessage orderPlaced = orderPlacedMessage();
        Message message = message(0);
        StockReservation reserved = StockReservation.reserved(EVENT_ID, ORDER_ID, Instant.now());
        when(reader.read(any(byte[].class))).thenReturn(Optional.of(orderPlaced));
        when(reservationService.reserve(orderPlaced)).thenReturn(reserved);
        doThrow(new IllegalStateException("broker down"))
                .when(resultPublisher).publish(reserved);

        listener.onOrderPlaced(message, channel);

        verify(channel).basicNack(DELIVERY_TAG, false, false);
        verify(channel, never()).basicAck(DELIVERY_TAG, false);
    }

    @Test
    void replaysTheStoredResultOnAUniqueConflict() throws Exception {
        OrderPlacedMessage orderPlaced = orderPlacedMessage();
        Message message = message(0);
        StockReservation stored = StockReservation.reserved(EVENT_ID, ORDER_ID, Instant.now());
        when(reader.read(any(byte[].class))).thenReturn(Optional.of(orderPlaced));
        when(reservationService.reserve(orderPlaced)).thenThrow(new DataIntegrityViolationException("duplicate"));
        when(reservationService.find(ORDER_ID)).thenReturn(Optional.of(stored));

        listener.onOrderPlaced(message, channel);

        verify(resultPublisher).publish(stored);
        verify(channel).basicAck(DELIVERY_TAG, false);
        verify(channel, never()).basicNack(DELIVERY_TAG, false, false);
    }

    @Test
    void nacksOnAUniqueConflictWithoutAStoredResult() throws Exception {
        OrderPlacedMessage orderPlaced = orderPlacedMessage();
        Message message = message(0);
        when(reader.read(any(byte[].class))).thenReturn(Optional.of(orderPlaced));
        when(reservationService.reserve(orderPlaced)).thenThrow(new DataIntegrityViolationException("duplicate"));
        when(reservationService.find(ORDER_ID)).thenReturn(Optional.empty());

        listener.onOrderPlaced(message, channel);

        verify(channel).basicNack(DELIVERY_TAG, false, false);
        verifyNoInteractions(resultPublisher);
    }

    @Test
    void nacksWhenReplayingTheStoredResultFailsToPublish() throws Exception {
        OrderPlacedMessage orderPlaced = orderPlacedMessage();
        Message message = message(0);
        StockReservation stored = StockReservation.reserved(EVENT_ID, ORDER_ID, Instant.now());
        when(reader.read(any(byte[].class))).thenReturn(Optional.of(orderPlaced));
        when(reservationService.reserve(orderPlaced)).thenThrow(new DataIntegrityViolationException("duplicate"));
        when(reservationService.find(ORDER_ID)).thenReturn(Optional.of(stored));
        doThrow(new IllegalStateException("broker down"))
                .when(resultPublisher).publish(stored);

        listener.onOrderPlaced(message, channel);

        verify(channel).basicNack(DELIVERY_TAG, false, false);
        verify(channel, never()).basicAck(DELIVERY_TAG, false);
    }

    private static OrderPlacedMessage orderPlacedMessage() {
        return new OrderPlacedMessage(EVENT_ID, ORDER_ID, List.of(new OrderPlacedItem(PRODUCT_ID, 1)));
    }

    private static Message message(int retries) {
        MessageProperties properties = new MessageProperties();
        properties.setDeliveryTag(DELIVERY_TAG);
        if (retries > 0) {
            List<Map<String, ?>> xDeath = new ArrayList<>();
            for (int death = 0; death < retries; death++) {
                xDeath.add(Map.of("queue", RetryAttempts.RETRY_QUEUE, "count", 1L, "reason", "expired"));
            }
            properties.setHeader("x-death", xDeath);
        }
        return new Message("{}".getBytes(StandardCharsets.UTF_8), properties);
    }
}
