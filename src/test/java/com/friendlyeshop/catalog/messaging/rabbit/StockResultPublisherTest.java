package com.friendlyeshop.catalog.messaging.rabbit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;

import com.friendlyeshop.catalog.exception.StockResultPublishException;
import com.friendlyeshop.catalog.model.StockReservation;
import com.friendlyeshop.catalog.model.enums.RejectionReason;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

@ExtendWith(MockitoExtension.class)
class StockResultPublisherTest {

    private static final String EXCHANGE = "stock";

    private static final UUID ORDER_ID = UUID.fromString("aaaaaaaa-0000-4000-8000-000000000001");

    private static final UUID EVENT_ID = UUID.fromString("bbbbbbbb-0000-4000-8000-000000000002");

    private static final Instant AT = Instant.parse("2026-11-25T12:00:00Z");

    @Mock
    private RabbitTemplate rabbitTemplate;

    private StockResultPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new StockResultPublisher(rabbitTemplate);
    }

    @Test
    void publishesReservedResultToTheStockExchange() {
        StockReservation reservation = StockReservation.reserved(EVENT_ID, ORDER_ID, AT);
        confirmWith(true);

        publisher.publish(reservation);

        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(rabbitTemplate).convertAndSend(eq(EXCHANGE), eq("stock.reserved"), payload.capture(),
                any(CorrelationData.class));
        assertThat(payload.getValue()).isEqualTo(new StockReservedMessage(EVENT_ID, ORDER_ID, AT));
    }

    @Test
    void publishesRejectedResultToTheStockExchange() {
        StockReservation reservation = StockReservation.rejected(
                EVENT_ID, ORDER_ID, RejectionReason.OUT_OF_STOCK, AT);
        confirmWith(true);

        publisher.publish(reservation);

        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(rabbitTemplate).convertAndSend(eq(EXCHANGE), eq("stock.rejected"), payload.capture(),
                any(CorrelationData.class));
        assertThat(payload.getValue())
                .isEqualTo(new StockRejectedMessage(EVENT_ID, ORDER_ID, RejectionReason.OUT_OF_STOCK, AT));
    }

    @Test
    void failsWhenTheBrokerDoesNotAcknowledgeTheResult() {
        StockReservation reservation = StockReservation.reserved(EVENT_ID, ORDER_ID, AT);
        confirmWith(false);

        assertThatThrownBy(() -> publisher.publish(reservation))
                .isInstanceOf(StockResultPublishException.class)
                .hasMessageContaining("rejected");
    }

    @Test
    void failsWhenTheConfirmationFails() {
        StockReservation reservation = StockReservation.reserved(EVENT_ID, ORDER_ID, AT);
        doAnswer(invocation -> {
            CorrelationData correlation = invocation.getArgument(3);
            correlation.getFuture().completeExceptionally(new AmqpException("connection lost"));
            return null;
        }).when(rabbitTemplate).convertAndSend(eq(EXCHANGE), eq("stock.reserved"), any(Object.class),
                any(CorrelationData.class));

        assertThatThrownBy(() -> publisher.publish(reservation))
                .isInstanceOf(StockResultPublishException.class)
                .hasMessageContaining("did not confirm");
    }

    private void confirmWith(boolean acknowledged) {
        doAnswer(invocation -> {
            CorrelationData correlation = invocation.getArgument(3);
            correlation.getFuture().complete(new CorrelationData.Confirm(acknowledged, "rejected"));
            return null;
        }).when(rabbitTemplate).convertAndSend(eq(EXCHANGE), any(String.class), any(Object.class),
                any(CorrelationData.class));
    }
}
