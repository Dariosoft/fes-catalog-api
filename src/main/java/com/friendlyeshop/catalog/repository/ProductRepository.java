package com.friendlyeshop.catalog.repository;

import com.friendlyeshop.catalog.model.Product;
import com.friendlyeshop.catalog.model.enums.ProductStage;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<Product, UUID> {

    Page<Product> findByOwnerAccountIdAndDeletedAtIsNull(UUID ownerAccountId, Pageable pageable);

    Page<Product> findByOwnerAccountIdAndNameContainingIgnoreCaseAndDeletedAtIsNull(
            UUID ownerAccountId, String name, Pageable pageable);

    Optional<Product> findByIdAndOwnerAccountIdAndDeletedAtIsNull(UUID id, UUID ownerAccountId);

    List<Product> findByOwnerAccountIdAndStageAndDeletedAtIsNull(UUID ownerAccountId, ProductStage stage);

    /**
     * Returns every product visible in the public storefront. The ordering is supplied by the given
     * {@link Pageable} so callers keep control of the page size and the stable sort.
     */
    @Query("""
            select p from Product p
            where p.stage = :stage
              and p.deletedAt is null
              and p.ownerAccountId is not null
              and (p.stock is null or p.stock > :minimumStock)
            """)
    Page<Product> findPublished(
            @Param("stage") ProductStage stage,
            @Param("minimumStock") int minimumStock,
            Pageable pageable);

    /**
     * Same public visibility rules as {@link #findPublished}, restricted to names containing the given
     * text regardless of letter case. Accents stay significant.
     */
    @Query("""
            select p from Product p
            where p.stage = :stage
              and p.deletedAt is null
              and p.ownerAccountId is not null
              and (p.stock is null or p.stock > :minimumStock)
              and lower(p.name) like lower(concat('%', :name, '%'))
            """)
    Page<Product> findPublishedByName(
            @Param("stage") ProductStage stage,
            @Param("minimumStock") int minimumStock,
            @Param("name") String name,
            Pageable pageable);
}
