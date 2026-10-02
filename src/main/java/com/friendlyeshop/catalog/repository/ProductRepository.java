package com.friendlyeshop.catalog.repository;

import com.friendlyeshop.catalog.model.Product;
import com.friendlyeshop.catalog.model.ProductStage;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<Product, UUID> {

    Page<Product> findByOwnerAccountIdAndDeletedAtIsNull(UUID ownerAccountId, Pageable pageable);

    Optional<Product> findByIdAndOwnerAccountIdAndDeletedAtIsNull(UUID id, UUID ownerAccountId);

    List<Product> findByOwnerAccountIdAndStageAndDeletedAtIsNull(UUID ownerAccountId, ProductStage stage);
}
