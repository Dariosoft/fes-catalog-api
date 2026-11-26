package com.friendlyeshop.catalog.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.friendlyeshop.catalog.messaging.rabbit.model.OrderPlacedItem;
import com.friendlyeshop.catalog.messaging.rabbit.model.OrderPlacedMessage;
import com.friendlyeshop.catalog.model.Product;
import com.friendlyeshop.catalog.model.StockReservation;
import com.friendlyeshop.catalog.model.dto.in.ProductDetails;
import com.friendlyeshop.catalog.model.enums.Currency;
import com.friendlyeshop.catalog.model.enums.RejectionReason;
import com.friendlyeshop.catalog.model.enums.ReservationStatus;
import com.friendlyeshop.catalog.repository.ProductRepository;
import com.friendlyeshop.catalog.repository.StockReservationRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StockReservationServiceTest {

    private static final UUID ORDER_ID = UUID.fromString("aaaaaaaa-0000-4000-8000-000000000001");

    private static final UUID EVENT_ID = UUID.fromString("bbbbbbbb-0000-4000-8000-000000000002");

    private static final UUID LOW_PRODUCT = UUID.fromString("10000000-0000-4000-8000-000000000001");

    private static final UUID HIGH_PRODUCT = UUID.fromString("20000000-0000-4000-8000-000000000002");

    @Mock
    private StockReservationRepository stockReservationRepository;

    @Mock
    private ProductRepository productRepository;

    private StockReservationService service;

    @BeforeEach
    void setUp() {
        service = new StockReservationService(stockReservationRepository, productRepository);
    }

    @Test
    void returnsStoredReservationWithoutTouchingProducts() {
        StockReservation stored = StockReservation.reserved(EVENT_ID, ORDER_ID, Instant.parse("2026-11-25T10:00:00Z"));
        when(stockReservationRepository.findById(ORDER_ID)).thenReturn(Optional.of(stored));

        StockReservation result = service.reserve(message(ORDER_ID, EVENT_ID, item(LOW_PRODUCT, 1)));

        assertThat(result).isSameAs(stored);
        verifyNoInteractions(productRepository);
        verify(stockReservationRepository, never()).saveAndFlush(any());
    }

    @Test
    void returnsStoredRejectionEvenWithADifferentEventId() {
        UUID anotherEvent = UUID.randomUUID();
        StockReservation stored = StockReservation.rejected(
                EVENT_ID, ORDER_ID, RejectionReason.OUT_OF_STOCK, Instant.parse("2026-11-25T10:00:00Z"));
        when(stockReservationRepository.findById(ORDER_ID)).thenReturn(Optional.of(stored));

        StockReservation result = service.reserve(message(ORDER_ID, anotherEvent, item(LOW_PRODUCT, 1)));

        assertThat(result).isSameAs(stored);
        assertThat(result.getEventId()).isEqualTo(EVENT_ID);
        verifyNoInteractions(productRepository);
    }

    @Test
    void rejectsMissingItemsAsInvalidOrder() {
        StockReservation result = service.reserve(new OrderPlacedMessage(EVENT_ID, ORDER_ID, List.of()));

        assertRejected(result, RejectionReason.INVALID_ORDER);
        verifyNoInteractions(productRepository);
    }

    @Test
    void rejectsNullItemsAsInvalidOrder() {
        StockReservation result = service.reserve(new OrderPlacedMessage(EVENT_ID, ORDER_ID, null));

        assertRejected(result, RejectionReason.INVALID_ORDER);
        verifyNoInteractions(productRepository);
    }

    @Test
    void rejectsItemsWithoutProductIdAsInvalidOrder() {
        StockReservation result = service.reserve(message(ORDER_ID, EVENT_ID, new OrderPlacedItem(null, 1)));

        assertRejected(result, RejectionReason.INVALID_ORDER);
        verifyNoInteractions(productRepository);
    }

    @Test
    void rejectsMissingQuantityAsInvalidQuantity() {
        StockReservation result = service.reserve(message(ORDER_ID, EVENT_ID, item(LOW_PRODUCT, null)));

        assertRejected(result, RejectionReason.INVALID_QUANTITY);
        verifyNoInteractions(productRepository);
    }

    @Test
    void rejectsNonPositiveQuantityAsInvalidQuantity() {
        assertRejected(service.reserve(message(ORDER_ID, EVENT_ID, item(LOW_PRODUCT, 0))),
                RejectionReason.INVALID_QUANTITY);
        assertRejected(service.reserve(message(UUID.randomUUID(), EVENT_ID, item(LOW_PRODUCT, -2))),
                RejectionReason.INVALID_QUANTITY);
    }

    @Test
    void structuralProblemsTakePrecedenceOverQuantities() {
        StockReservation result = service.reserve(
                message(ORDER_ID, EVENT_ID, new OrderPlacedItem(null, null), item(LOW_PRODUCT, 1)));

        assertRejected(result, RejectionReason.INVALID_ORDER);
    }

    @Test
    void quantityProblemsTakePrecedenceOverExistence() {
        StockReservation result = service.reserve(
                message(ORDER_ID, EVENT_ID, item(LOW_PRODUCT, 0), item(HIGH_PRODUCT, 1)));

        assertRejected(result, RejectionReason.INVALID_QUANTITY);
        verifyNoInteractions(productRepository);
    }

    @Test
    void rejectsOverflowingRepeatedQuantity() {
        StockReservation result = service.reserve(message(ORDER_ID, EVENT_ID,
                item(LOW_PRODUCT, Integer.MAX_VALUE), item(LOW_PRODUCT, 1)));

        assertRejected(result, RejectionReason.INVALID_QUANTITY);
        verifyNoInteractions(productRepository);
    }

    @Test
    void rejectsMissingProductAsNotFound() {
        when(productRepository.findByIdForUpdate(LOW_PRODUCT)).thenReturn(Optional.empty());

        StockReservation result = service.reserve(message(ORDER_ID, EVENT_ID, item(LOW_PRODUCT, 1)));

        assertRejected(result, RejectionReason.PRODUCT_NOT_FOUND);
    }

    @Test
    void existenceTakesPrecedenceOverAvailabilityAndStock() {
        Product exhausted = product(0, true);
        when(productRepository.findByIdForUpdate(LOW_PRODUCT)).thenReturn(Optional.of(exhausted));
        when(productRepository.findByIdForUpdate(HIGH_PRODUCT)).thenReturn(Optional.empty());

        StockReservation result = service.reserve(
                message(ORDER_ID, EVENT_ID, item(LOW_PRODUCT, 10), item(HIGH_PRODUCT, 1)));

        assertRejected(result, RejectionReason.PRODUCT_NOT_FOUND);
        assertThat(exhausted.getStock()).isZero();
    }

    @Test
    void rejectsUnpublishedProductAsNotAvailable() {
        Product draft = product(10, false);
        when(productRepository.findByIdForUpdate(LOW_PRODUCT)).thenReturn(Optional.of(draft));

        StockReservation result = service.reserve(message(ORDER_ID, EVENT_ID, item(LOW_PRODUCT, 1)));

        assertRejected(result, RejectionReason.PRODUCT_NOT_AVAILABLE);
        assertThat(draft.getStock()).isEqualTo(10);
    }

    @Test
    void availabilityTakesPrecedenceOverStock() {
        Product scarce = product(1, true);
        Product draft = product(10, false);
        when(productRepository.findByIdForUpdate(LOW_PRODUCT)).thenReturn(Optional.of(scarce));
        when(productRepository.findByIdForUpdate(HIGH_PRODUCT)).thenReturn(Optional.of(draft));

        StockReservation result = service.reserve(
                message(ORDER_ID, EVENT_ID, item(LOW_PRODUCT, 10), item(HIGH_PRODUCT, 1)));

        assertRejected(result, RejectionReason.PRODUCT_NOT_AVAILABLE);
        assertThat(scarce.getStock()).isEqualTo(1);
    }

    @Test
    void rejectsWhenStockIsNotEnoughWithoutTouchingAnyStock() {
        Product available = product(5, true);
        Product scarce = product(2, true);
        when(productRepository.findByIdForUpdate(LOW_PRODUCT)).thenReturn(Optional.of(available));
        when(productRepository.findByIdForUpdate(HIGH_PRODUCT)).thenReturn(Optional.of(scarce));

        StockReservation result = service.reserve(
                message(ORDER_ID, EVENT_ID, item(LOW_PRODUCT, 1), item(HIGH_PRODUCT, 3)));

        assertRejected(result, RejectionReason.OUT_OF_STOCK);
        assertThat(available.getStock()).isEqualTo(5);
        assertThat(scarce.getStock()).isEqualTo(2);
    }

    @Test
    void reservesEveryProductInAscendingProductOrder() {
        Product first = product(5, true);
        Product second = product(7, true);
        when(productRepository.findByIdForUpdate(LOW_PRODUCT)).thenReturn(Optional.of(first));
        when(productRepository.findByIdForUpdate(HIGH_PRODUCT)).thenReturn(Optional.of(second));

        StockReservation result = service.reserve(
                message(ORDER_ID, EVENT_ID, item(HIGH_PRODUCT, 3), item(LOW_PRODUCT, 2)));

        assertThat(result.getStatus()).isEqualTo(ReservationStatus.RESERVED);
        assertThat(result.getOrderId()).isEqualTo(ORDER_ID);
        assertThat(result.getEventId()).isEqualTo(EVENT_ID);
        assertThat(result.getReservedAt()).isNotNull();
        assertThat(first.getStock()).isEqualTo(3);
        assertThat(second.getStock()).isEqualTo(4);
        InOrder order = inOrder(productRepository);
        order.verify(productRepository).findByIdForUpdate(LOW_PRODUCT);
        order.verify(productRepository).findByIdForUpdate(HIGH_PRODUCT);
        verify(stockReservationRepository).saveAndFlush(any(StockReservation.class));
    }

    @Test
    void aggregatesRepeatedItemsOfTheSameProduct() {
        Product product = product(10, true);
        when(productRepository.findByIdForUpdate(LOW_PRODUCT)).thenReturn(Optional.of(product));

        StockReservation result = service.reserve(message(ORDER_ID, EVENT_ID,
                item(LOW_PRODUCT, 2), item(LOW_PRODUCT, 3)));

        assertThat(result.getStatus()).isEqualTo(ReservationStatus.RESERVED);
        assertThat(product.getStock()).isEqualTo(5);
        verify(productRepository).findByIdForUpdate(LOW_PRODUCT);
    }

    @Test
    void treatsNullStockAsUnlimitedAndKeepsItUnchanged() {
        Product unlimited = product(null, true);
        when(productRepository.findByIdForUpdate(LOW_PRODUCT)).thenReturn(Optional.of(unlimited));

        StockReservation result = service.reserve(message(ORDER_ID, EVENT_ID, item(LOW_PRODUCT, 100)));

        assertThat(result.getStatus()).isEqualTo(ReservationStatus.RESERVED);
        assertThat(unlimited.getStock()).isNull();
    }

    @Test
    void leavesStockAtZeroWhenReservingExactlyTheAvailableUnits() {
        Product product = product(3, true);
        when(productRepository.findByIdForUpdate(LOW_PRODUCT)).thenReturn(Optional.of(product));

        StockReservation result = service.reserve(message(ORDER_ID, EVENT_ID, item(LOW_PRODUCT, 3)));

        assertThat(result.getStatus()).isEqualTo(ReservationStatus.RESERVED);
        assertThat(product.getStock()).isZero();
    }

    @Test
    void findsStoredReservationByOrderId() {
        StockReservation stored = StockReservation.reserved(EVENT_ID, ORDER_ID, Instant.now());
        when(stockReservationRepository.findById(ORDER_ID)).thenReturn(Optional.of(stored));

        assertThat(service.find(ORDER_ID)).contains(stored);
    }

    private void assertRejected(StockReservation reservation, RejectionReason reason) {
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.REJECTED);
        assertThat(reservation.getReason()).isEqualTo(reason);
        assertThat(reservation.getRejectedAt()).isNotNull();
        assertThat(reservation.getReservedAt()).isNull();
    }

    private static Product product(Integer stock, boolean published) {
        Product product = Product.openNew(UUID.randomUUID(),
                new ProductDetails("Producto", null, new BigDecimal("10.00"), Currency.ARS, stock));
        if (published) {
            product.publish();
        }
        return product;
    }

    private static OrderPlacedItem item(UUID productId, Integer quantity) {
        return new OrderPlacedItem(productId, quantity);
    }

    private static OrderPlacedMessage message(UUID orderId, UUID eventId, OrderPlacedItem... items) {
        return new OrderPlacedMessage(eventId, orderId, List.of(items));
    }
}
