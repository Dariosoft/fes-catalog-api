package com.friendlyeshop.catalog.repository;

import com.friendlyeshop.catalog.model.StockReservation;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StockReservationRepository extends JpaRepository<StockReservation, UUID> {
}
