package com.friendlyeshop.catalog.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.friendlyeshop.catalog.messaging.rabbit.OrderPlacedItem;
import com.friendlyeshop.catalog.messaging.rabbit.OrderPlacedMessage;
import com.friendlyeshop.catalog.model.Product;
import com.friendlyeshop.catalog.model.StockReservation;
import com.friendlyeshop.catalog.model.dto.in.ProductDetails;
import com.friendlyeshop.catalog.model.enums.Currency;
import com.friendlyeshop.catalog.model.enums.RejectionReason;
import com.friendlyeshop.catalog.model.enums.ReservationStatus;
import com.friendlyeshop.catalog.repository.ProductRepository;
import com.friendlyeshop.catalog.repository.StockReservationRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@Import(StockReservationService.class)
@Testcontainers
class StockReservationServiceIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17");

    @Autowired
    private StockReservationService service;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private StockReservationRepository stockReservationRepository;

    @AfterEach
    void cleanUp() {
        stockReservationRepository.deleteAll();
        productRepository.deleteAll();
    }

    @Test
    void reservesStockAndPersistsTheResult() {
        Product product = publishedProduct(5);
        UUID orderId = UUID.randomUUID();

        StockReservation result = service.reserve(message(orderId, UUID.randomUUID(), product.getId(), 2));

        assertThat(result.getStatus()).isEqualTo(ReservationStatus.RESERVED);
        assertThat(stockReservationRepository.findById(orderId)).isPresent();
        assertThat(stockOf(product.getId())).isEqualTo(3);
    }

    @Test
    void replayingTheSameOrderDoesNotReserveTwice() {
        Product product = publishedProduct(5);
        UUID orderId = UUID.randomUUID();
        service.reserve(message(orderId, UUID.randomUUID(), product.getId(), 2));

        StockReservation replay = service.reserve(message(orderId, UUID.randomUUID(), product.getId(), 2));

        assertThat(replay.getStatus()).isEqualTo(ReservationStatus.RESERVED);
        assertThat(service.find(orderId)).isPresent();
        assertThat(stockReservationRepository.findAll()).hasSize(1);
        assertThat(stockOf(product.getId())).isEqualTo(3);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentOrdersCannotOversellTheSameProduct() throws Exception {
        Product product = publishedProduct(5);
        List<OrderPlacedMessage> orders = List.of(
                message(UUID.randomUUID(), UUID.randomUUID(), product.getId(), 3),
                message(UUID.randomUUID(), UUID.randomUUID(), product.getId(), 3));

        List<Attempt> attempts = runConcurrently(orders);

        assertThat(stockOf(product.getId())).isEqualTo(2);
        List<StockReservation> stored = stockReservationRepository.findAll();
        assertThat(stored).hasSize(2);
        assertThat(stored).extracting(StockReservation::getStatus)
                .containsExactlyInAnyOrder(ReservationStatus.RESERVED, ReservationStatus.REJECTED);
        assertThat(stored).filteredOn(reservation -> reservation.getStatus() == ReservationStatus.REJECTED)
                .extracting(StockReservation::getReason)
                .containsExactly(RejectionReason.OUT_OF_STOCK);
        assertThat(attempts).hasSize(2);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentDeliveriesOfTheSameOrderReserveOnlyOnce() throws Exception {
        Product product = publishedProduct(10);
        UUID orderId = UUID.randomUUID();
        List<OrderPlacedMessage> deliveries = List.of(
                message(orderId, UUID.randomUUID(), product.getId(), 1),
                message(orderId, UUID.randomUUID(), product.getId(), 1));

        List<Attempt> attempts = runConcurrently(deliveries);

        assertThat(stockOf(product.getId())).isEqualTo(9);
        assertThat(stockReservationRepository.findAll()).hasSize(1);
        assertThat(attempts).filteredOn(Attempt::failed).hasSizeLessThanOrEqualTo(1);
    }

    private List<Attempt> runConcurrently(List<OrderPlacedMessage> messages) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(messages.size());
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Attempt>> futures = new ArrayList<>();
            for (OrderPlacedMessage message : messages) {
                futures.add(executor.submit(() -> {
                    start.await();
                    return attempt(message);
                }));
            }
            start.countDown();
            List<Attempt> attempts = new ArrayList<>();
            for (Future<Attempt> future : futures) {
                attempts.add(future.get());
            }
            return attempts;
        } finally {
            executor.shutdownNow();
        }
    }

    private Attempt attempt(OrderPlacedMessage message) {
        try {
            return new Attempt(service.reserve(message), null);
        } catch (RuntimeException failure) {
            return new Attempt(null, failure);
        }
    }

    private Product publishedProduct(int stock) {
        Product product = Product.openNew(UUID.randomUUID(),
                new ProductDetails("Producto", null, new BigDecimal("10.00"), Currency.ARS, stock));
        product.publish();
        return productRepository.saveAndFlush(product);
    }

    private int stockOf(UUID productId) {
        return productRepository.findById(productId).orElseThrow().getStock();
    }

    private static OrderPlacedMessage message(UUID orderId, UUID eventId, UUID productId, int quantity) {
        return new OrderPlacedMessage(eventId, orderId, List.of(new OrderPlacedItem(productId, quantity)));
    }

    private record Attempt(StockReservation reservation, RuntimeException failure) {

        boolean failed() {
            return failure != null;
        }
    }
}
