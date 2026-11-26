package com.friendlyeshop.catalog.service;

import com.friendlyeshop.catalog.messaging.rabbit.model.OrderPlacedItem;
import com.friendlyeshop.catalog.messaging.rabbit.model.OrderPlacedMessage;
import com.friendlyeshop.catalog.model.Product;
import com.friendlyeshop.catalog.model.StockReservation;
import com.friendlyeshop.catalog.model.enums.RejectionReason;
import com.friendlyeshop.catalog.repository.ProductRepository;
import com.friendlyeshop.catalog.repository.StockReservationRepository;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StockReservationService {

    private final StockReservationRepository stockReservationRepository;

    private final ProductRepository productRepository;

    public StockReservationService(StockReservationRepository stockReservationRepository,
            ProductRepository productRepository) {
        this.stockReservationRepository = stockReservationRepository;
        this.productRepository = productRepository;
    }

    @Transactional
    public StockReservation reserve(OrderPlacedMessage message) {
        Optional<StockReservation> stored = stockReservationRepository.findById(message.orderId());
        if (stored.isPresent()) {
            return stored.get();
        }
        Optional<RejectionReason> structuralIssue = validateStructure(message.items());
        if (structuralIssue.isPresent()) {
            return reject(message, structuralIssue.get());
        }
        Optional<RejectionReason> quantityIssue = validateQuantities(message.items());
        if (quantityIssue.isPresent()) {
            return reject(message, quantityIssue.get());
        }
        Optional<Map<UUID, Integer>> aggregated = aggregateQuantities(message.items());
        if (aggregated.isEmpty()) {
            return reject(message, RejectionReason.INVALID_QUANTITY);
        }
        return reserveQuantities(message, aggregated.get());
    }

    @Transactional(readOnly = true)
    public Optional<StockReservation> find(UUID orderId) {
        return stockReservationRepository.findById(orderId);
    }

    private Optional<RejectionReason> validateStructure(List<OrderPlacedItem> items) {
        if (items == null || items.isEmpty()) {
            return Optional.of(RejectionReason.INVALID_ORDER);
        }
        for (OrderPlacedItem item : items) {
            if (item == null || item.productId() == null) {
                return Optional.of(RejectionReason.INVALID_ORDER);
            }
        }
        return Optional.empty();
    }

    private Optional<RejectionReason> validateQuantities(List<OrderPlacedItem> items) {
        for (OrderPlacedItem item : items) {
            Integer quantity = item.quantity();
            if (quantity == null || quantity <= 0) {
                return Optional.of(RejectionReason.INVALID_QUANTITY);
            }
        }
        return Optional.empty();
    }

    private Optional<Map<UUID, Integer>> aggregateQuantities(List<OrderPlacedItem> items) {
        Map<UUID, Long> totals = new TreeMap<>();
        for (OrderPlacedItem item : items) {
            long accumulated = totals.getOrDefault(item.productId(), 0L) + item.quantity();
            totals.put(item.productId(), accumulated);
        }
        Map<UUID, Integer> quantities = new TreeMap<>();
        for (Map.Entry<UUID, Long> entry : totals.entrySet()) {
            if (entry.getValue() > Integer.MAX_VALUE) {
                return Optional.empty();
            }
            quantities.put(entry.getKey(), entry.getValue().intValue());
        }
        return Optional.of(quantities);
    }

    private StockReservation reserveQuantities(OrderPlacedMessage message, Map<UUID, Integer> quantities) {
        Map<UUID, Product> products = lockProducts(quantities.keySet());
        if (products.size() < quantities.size()) {
            return reject(message, RejectionReason.PRODUCT_NOT_FOUND);
        }
        if (products.values().stream().anyMatch(product -> !product.isReservable())) {
            return reject(message, RejectionReason.PRODUCT_NOT_AVAILABLE);
        }
        for (Map.Entry<UUID, Integer> entry : quantities.entrySet()) {
            if (!products.get(entry.getKey()).canCover(entry.getValue())) {
                return reject(message, RejectionReason.OUT_OF_STOCK);
            }
        }
        quantities.forEach((productId, quantity) -> products.get(productId).reserve(quantity));
        StockReservation reservation = StockReservation.reserved(message.eventId(), message.orderId(), Instant.now());
        stockReservationRepository.saveAndFlush(reservation);
        return reservation;
    }

    private Map<UUID, Product> lockProducts(Set<UUID> productIds) {
        Map<UUID, Product> locked = new LinkedHashMap<>();
        for (UUID productId : productIds) {
            productRepository.findByIdForUpdate(productId)
                    .ifPresent(product -> locked.put(productId, product));
        }
        return locked;
    }

    private StockReservation reject(OrderPlacedMessage message, RejectionReason reason) {
        StockReservation rejected = StockReservation.rejected(
                message.eventId(), message.orderId(), reason, Instant.now());
        stockReservationRepository.saveAndFlush(rejected);
        return rejected;
    }
}
